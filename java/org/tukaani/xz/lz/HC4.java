/*
 * Hash Chain match finder with 2-, 3-, and 4-byte hashing
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lz;

final class HC4 extends LZEncoder {
    /**
     * Gets approximate memory usage of the match finder as kibibytes.
     */
    static int getMemoryUsage(final int dictSize) {
	return Hash234.getMemoryUsage(dictSize) + dictSize / (1024 / 4) + 10;
    }

    private final Hash234 hash;
    private final int[] chain;
    private final Matches matches;

    private final int depthLimit;
    private final int cyclicSize;
    private int cyclicPos = -1;

    private int lzPos;

    /**
     * Creates a new LZEncoder with the HC4 match finder. See
     * <code>LZEncoder.getInstance</code> for parameter descriptions.
     */
    HC4(final int dictSize, final int beforeSizeMin, final int readAheadMax, final int niceLen, final int matchLenMax,
	    final int depthLimit) {
	super(dictSize, beforeSizeMin, readAheadMax, niceLen, matchLenMax);

	this.hash = new Hash234(dictSize);

	// +1 because we need dictSize bytes of history + the current byte.
	this.cyclicSize = dictSize + 1;
	this.chain = new int[this.cyclicSize];
	this.lzPos = this.cyclicSize;

	// Substracting 1 because the shortest match that this match
	// finder can find is 2 bytes, so there's no need to reserve
	// space for one-byte matches.
	this.matches = new Matches(niceLen - 1);

	// Use a default depth limit if no other value was specified.
	// The default is just something based on experimentation;
	// it's nothing magic.
	this.depthLimit = depthLimit > 0 ? depthLimit : 4 + niceLen / 4;
    }

    @Override
    public Matches getMatches() {
	this.matches.count = 0;
	int matchLenLimit = this.matchLenMax;
	int niceLenLimit = this.niceLen;
	final int avail = this.movePos();

	if (avail < matchLenLimit) {
	    if (avail == 0) {
		return this.matches;
	    }

	    matchLenLimit = avail;
	    if (niceLenLimit > avail) {
		niceLenLimit = avail;
	    }
	}

	this.hash.calcHashes(this.buf, this.readPos);
	int delta2 = this.lzPos - this.hash.getHash2Pos();
	final int delta3 = this.lzPos - this.hash.getHash3Pos();
	int currentMatch = this.hash.getHash4Pos();
	this.hash.updateTables(this.lzPos);

	this.chain[this.cyclicPos] = currentMatch;

	int lenBest = 0;

	// See if the hash from the first two bytes found a match.
	// The hashing algorithm guarantees that if the first byte
	// matches, also the second byte does, so there's no need to
	// test the second byte.
	if (delta2 < this.cyclicSize && this.buf[this.readPos - delta2] == this.buf[this.readPos]) {
	    lenBest = 2;
	    this.matches.len[0] = 2;
	    this.matches.dist[0] = delta2 - 1;
	    this.matches.count = 1;
	}

	// See if the hash from the first three bytes found a match that
	// is different from the match possibly found by the two-byte hash.
	// Also here the hashing algorithm guarantees that if the first byte
	// matches, also the next two bytes do.
	if (delta2 != delta3 && delta3 < this.cyclicSize && this.buf[this.readPos - delta3] == this.buf[this.readPos]) {
	    lenBest = 3;
	    this.matches.dist[this.matches.count++] = delta3 - 1;
	    delta2 = delta3;
	}

	// If a match was found, see how long it is.
	if (this.matches.count > 0) {
	    while (lenBest < matchLenLimit
		    && this.buf[this.readPos + lenBest - delta2] == this.buf[this.readPos + lenBest]) {
		++lenBest;
	    }

	    this.matches.len[this.matches.count - 1] = lenBest;

	    // Return if it is long enough (niceLen or reached the end of
	    // the dictionary).
	    if (lenBest >= niceLenLimit) {
		return this.matches;
	    }
	}

	// Long enough match wasn't found so easily. Look for better matches
	// from the hash chain.
	if (lenBest < 3) {
	    lenBest = 3;
	}

	int depth = this.depthLimit;

	while (true) {
	    final int delta = this.lzPos - currentMatch;

	    // Return if the search depth limit has been reached or
	    // if the distance of the potential match exceeds the
	    // dictionary size.
	    if (depth-- == 0 || delta >= this.cyclicSize) {
		return this.matches;
	    }

	    currentMatch = this.chain[this.cyclicPos - delta + (delta > this.cyclicPos ? this.cyclicSize : 0)];

	    // Test the first byte and the first new byte that would give us
	    // a match that is at least one byte longer than lenBest. This
	    // too short matches get quickly skipped.
	    if (this.buf[this.readPos + lenBest - delta] == this.buf[this.readPos + lenBest]
		    && this.buf[this.readPos - delta] == this.buf[this.readPos]) {
		// Calculate the length of the match.
		int len = 0;
		while (++len < matchLenLimit) {
		    if (this.buf[this.readPos + len - delta] != this.buf[this.readPos + len]) {
			break;
		    }
		}

		// Use the match if and only if it is better than the longest
		// match found so far.
		if (len > lenBest) {
		    lenBest = len;
		    this.matches.len[this.matches.count] = len;
		    this.matches.dist[this.matches.count] = delta - 1;
		    ++this.matches.count;

		    // Return if it is long enough (niceLen or reached the
		    // end of the dictionary).
		    if (len >= niceLenLimit) {
			return this.matches;
		    }
		}
	    }
	}
    }

    /**
     * Moves to the next byte, checks that there is enough available space, and
     * possibly normalizes the hash tables and the hash chain.
     *
     * @return number of bytes available, including the current byte
     */
    private int movePos() {
	final int avail = this.movePos(4, 4);

	if (avail != 0) {
	    if (++this.lzPos == Integer.MAX_VALUE) {
		final int normalizationOffset = Integer.MAX_VALUE - this.cyclicSize;
		this.hash.normalize(normalizationOffset);
		LZEncoder.normalize(this.chain, normalizationOffset);
		this.lzPos -= normalizationOffset;
	    }

	    if (++this.cyclicPos == this.cyclicSize) {
		this.cyclicPos = 0;
	    }
	}

	return avail;
    }

    @Override
    public void skip(int len) {
	assert len >= 0;

	while (len-- > 0) {
	    if (this.movePos() != 0) {
		// Update the hash chain and hash tables.
		this.hash.calcHashes(this.buf, this.readPos);
		this.chain[this.cyclicPos] = this.hash.getHash4Pos();
		this.hash.updateTables(this.lzPos);
	    }
	}
    }
}
