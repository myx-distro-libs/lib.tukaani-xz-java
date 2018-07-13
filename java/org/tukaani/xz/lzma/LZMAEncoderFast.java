/*
 * LZMAEncoderFast
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lzma;

import org.tukaani.xz.lz.LZEncoder;
import org.tukaani.xz.lz.Matches;
import org.tukaani.xz.rangecoder.RangeEncoder;

final class LZMAEncoderFast extends LZMAEncoder {
    private static final int EXTRA_SIZE_BEFORE = 1;
    private static final int EXTRA_SIZE_AFTER = LZMACoder.MATCH_LEN_MAX - 1;

    static int getMemoryUsage(final int dictSize, final int extraSizeBefore, final int mf) {
	return LZEncoder.getMemoryUsage(dictSize, Math.max(extraSizeBefore, LZMAEncoderFast.EXTRA_SIZE_BEFORE),
		LZMAEncoderFast.EXTRA_SIZE_AFTER, LZMACoder.MATCH_LEN_MAX, mf);
    }

    private Matches matches = null;

    LZMAEncoderFast(final RangeEncoder rc, final int lc, final int lp, final int pb, final int dictSize,
	    final int extraSizeBefore, final int niceLen, final int mf, final int depthLimit) {
	super(rc,
		LZEncoder.getInstance(dictSize, Math.max(extraSizeBefore, LZMAEncoderFast.EXTRA_SIZE_BEFORE),
			LZMAEncoderFast.EXTRA_SIZE_AFTER, niceLen, LZMACoder.MATCH_LEN_MAX, mf, depthLimit),
		lc, lp, pb, dictSize, niceLen);
    }

    private boolean changePair(final int smallDist, final int bigDist) {
	return smallDist < bigDist >>> 7;
    }

    @Override
    int getNextSymbol() {
	// Get the matches for the next byte unless readAhead indicates
	// that we already got the new matches during the previous call
	// to this function.
	if (this.readAhead == -1) {
	    this.matches = this.getMatches();
	}

	this.back = -1;

	// Get the number of bytes available in the dictionary, but
	// not more than the maximum match length. If there aren't
	// enough bytes remaining to encode a match at all, return
	// immediately to encode this byte as a literal.
	final int avail = Math.min(this.lz.getAvail(), LZMACoder.MATCH_LEN_MAX);
	if (avail < LZMACoder.MATCH_LEN_MIN) {
	    return 1;
	}

	// Look for a match from the previous four match distances.
	int bestRepLen = 0;
	int bestRepIndex = 0;
	for (int rep = 0; rep < LZMACoder.REPS; ++rep) {
	    final int len = this.lz.getMatchLen(this.reps[rep], avail);
	    if (len < LZMACoder.MATCH_LEN_MIN) {
		continue;
	    }

	    // If it is long enough, return it.
	    if (len >= this.niceLen) {
		this.back = rep;
		this.skip(len - 1);
		return len;
	    }

	    // Remember the index and length of the best repeated match.
	    if (len > bestRepLen) {
		bestRepIndex = rep;
		bestRepLen = len;
	    }
	}

	int mainLen = 0;
	int mainDist = 0;

	if (this.matches.count > 0) {
	    mainLen = this.matches.len[this.matches.count - 1];
	    mainDist = this.matches.dist[this.matches.count - 1];

	    if (mainLen >= this.niceLen) {
		this.back = mainDist + LZMACoder.REPS;
		this.skip(mainLen - 1);
		return mainLen;
	    }

	    while (this.matches.count > 1 && mainLen == this.matches.len[this.matches.count - 2] + 1) {
		if (!this.changePair(this.matches.dist[this.matches.count - 2], mainDist)) {
		    break;
		}

		--this.matches.count;
		mainLen = this.matches.len[this.matches.count - 1];
		mainDist = this.matches.dist[this.matches.count - 1];
	    }

	    if (mainLen == LZMACoder.MATCH_LEN_MIN && mainDist >= 0x80) {
		mainLen = 1;
	    }
	}

	if (bestRepLen >= LZMACoder.MATCH_LEN_MIN) {
	    if (bestRepLen + 1 >= mainLen || bestRepLen + 2 >= mainLen && mainDist >= 1 << 9
		    || bestRepLen + 3 >= mainLen && mainDist >= 1 << 15) {
		this.back = bestRepIndex;
		this.skip(bestRepLen - 1);
		return bestRepLen;
	    }
	}

	if (mainLen < LZMACoder.MATCH_LEN_MIN || avail <= LZMACoder.MATCH_LEN_MIN) {
	    return 1;
	}

	// Get the next match. Test if it is better than the current match.
	// If so, encode the current byte as a literal.
	this.matches = this.getMatches();

	if (this.matches.count > 0) {
	    final int newLen = this.matches.len[this.matches.count - 1];
	    final int newDist = this.matches.dist[this.matches.count - 1];

	    if (newLen >= mainLen && newDist < mainDist || newLen == mainLen + 1 && !this.changePair(mainDist, newDist)
		    || newLen > mainLen + 1 || newLen + 1 >= mainLen && mainLen >= LZMACoder.MATCH_LEN_MIN + 1
			    && this.changePair(newDist, mainDist)) {
		return 1;
	    }
	}

	final int limit = Math.max(mainLen - 1, LZMACoder.MATCH_LEN_MIN);
	for (int rep = 0; rep < LZMACoder.REPS; ++rep) {
	    if (this.lz.getMatchLen(this.reps[rep], limit) == limit) {
		return 1;
	    }
	}

	this.back = mainDist + LZMACoder.REPS;
	this.skip(mainLen - 2);
	return mainLen;
    }
}
