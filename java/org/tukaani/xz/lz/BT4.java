/*
 * Binary Tree match finder with 2-, 3-, and 4-byte hashing
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lz;

final class BT4 extends LZEncoder {
    static int getMemoryUsage(final int dictSize) {
	return Hash234.getMemoryUsage(dictSize) + dictSize / (1024 / 8) + 10;
    }

    private final Hash234 hash;
    private final int[] tree;
    private final Matches matches;

    private final int depthLimit;
    private final int cyclicSize;
    private int cyclicPos = -1;

    private int lzPos;

    BT4(final int dictSize, final int beforeSizeMin, final int readAheadMax, final int niceLen, final int matchLenMax,
	    final int depthLimit) {
	super(dictSize, beforeSizeMin, readAheadMax, niceLen, matchLenMax);

	this.cyclicSize = dictSize + 1;
	this.lzPos = this.cyclicSize;

	this.hash = new Hash234(dictSize);
	this.tree = new int[this.cyclicSize * 2];

	// Substracting 1 because the shortest match that this match
	// finder can find is 2 bytes, so there's no need to reserve
	// space for one-byte matches.
	this.matches = new Matches(niceLen - 1);

	this.depthLimit = depthLimit > 0 ? depthLimit : 16 + niceLen / 2;
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
		this.skip(niceLenLimit, currentMatch);
		return this.matches;
	    }
	}

	// Long enough match wasn't found so easily. Look for better matches
	// from the binary tree.
	if (lenBest < 3) {
	    lenBest = 3;
	}

	int depth = this.depthLimit;

	int ptr0 = (this.cyclicPos << 1) + 1;
	int ptr1 = this.cyclicPos << 1;
	int len0 = 0;
	int len1 = 0;

	while (true) {
	    final int delta = this.lzPos - currentMatch;

	    // Return if the search depth limit has been reached or
	    // if the distance of the potential match exceeds the
	    // dictionary size.
	    if (depth-- == 0 || delta >= this.cyclicSize) {
		this.tree[ptr0] = 0;
		this.tree[ptr1] = 0;
		return this.matches;
	    }

	    final int pair = this.cyclicPos - delta + (delta > this.cyclicPos ? this.cyclicSize : 0) << 1;
	    int len = Math.min(len0, len1);

	    if (this.buf[this.readPos + len - delta] == this.buf[this.readPos + len]) {
		while (++len < matchLenLimit) {
		    if (this.buf[this.readPos + len - delta] != this.buf[this.readPos + len]) {
			break;
		    }
		}

		if (len > lenBest) {
		    lenBest = len;
		    this.matches.len[this.matches.count] = len;
		    this.matches.dist[this.matches.count] = delta - 1;
		    ++this.matches.count;

		    if (len >= niceLenLimit) {
			this.tree[ptr1] = this.tree[pair];
			this.tree[ptr0] = this.tree[pair + 1];
			return this.matches;
		    }
		}
	    }

	    if ((this.buf[this.readPos + len - delta] & 0xFF) < (this.buf[this.readPos + len] & 0xFF)) {
		this.tree[ptr1] = currentMatch;
		ptr1 = pair + 1;
		currentMatch = this.tree[ptr1];
		len1 = len;
	    } else {
		this.tree[ptr0] = currentMatch;
		ptr0 = pair;
		currentMatch = this.tree[ptr0];
		len0 = len;
	    }
	}
    }

    private int movePos() {
	final int avail = this.movePos(this.niceLen, 4);

	if (avail != 0) {
	    if (++this.lzPos == Integer.MAX_VALUE) {
		final int normalizationOffset = Integer.MAX_VALUE - this.cyclicSize;
		this.hash.normalize(normalizationOffset);
		LZEncoder.normalize(this.tree, normalizationOffset);
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
	while (len-- > 0) {
	    int niceLenLimit = this.niceLen;
	    final int avail = this.movePos();

	    if (avail < niceLenLimit) {
		if (avail == 0) {
		    continue;
		}

		niceLenLimit = avail;
	    }

	    this.hash.calcHashes(this.buf, this.readPos);
	    final int currentMatch = this.hash.getHash4Pos();
	    this.hash.updateTables(this.lzPos);

	    this.skip(niceLenLimit, currentMatch);
	}
    }

    private void skip(final int niceLenLimit, int currentMatch) {
	int depth = this.depthLimit;

	int ptr0 = (this.cyclicPos << 1) + 1;
	int ptr1 = this.cyclicPos << 1;
	int len0 = 0;
	int len1 = 0;

	while (true) {
	    final int delta = this.lzPos - currentMatch;

	    if (depth-- == 0 || delta >= this.cyclicSize) {
		this.tree[ptr0] = 0;
		this.tree[ptr1] = 0;
		return;
	    }

	    final int pair = this.cyclicPos - delta + (delta > this.cyclicPos ? this.cyclicSize : 0) << 1;
	    int len = Math.min(len0, len1);

	    if (this.buf[this.readPos + len - delta] == this.buf[this.readPos + len]) {
		// No need to look for longer matches than niceLenLimit
		// because we only are updating the tree, not returning
		// matches found to the caller.
		do {
		    if (++len == niceLenLimit) {
			this.tree[ptr1] = this.tree[pair];
			this.tree[ptr0] = this.tree[pair + 1];
			return;
		    }
		} while (this.buf[this.readPos + len - delta] == this.buf[this.readPos + len]);
	    }

	    if ((this.buf[this.readPos + len - delta] & 0xFF) < (this.buf[this.readPos + len] & 0xFF)) {
		this.tree[ptr1] = currentMatch;
		ptr1 = pair + 1;
		currentMatch = this.tree[ptr1];
		len1 = len;
	    } else {
		this.tree[ptr0] = currentMatch;
		ptr0 = pair;
		currentMatch = this.tree[ptr0];
		len0 = len;
	    }
	}
    }
}
