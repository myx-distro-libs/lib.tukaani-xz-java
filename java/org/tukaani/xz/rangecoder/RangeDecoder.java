/*
 * RangeDecoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.rangecoder;

import java.io.IOException;

public abstract class RangeDecoder extends RangeCoder {
    int range = 0;
    int code = 0;

    public int decodeBit(final short[] probs, final int index) throws IOException {
	this.normalize();

	final int prob = probs[index];
	final int bound = (this.range >>> RangeCoder.BIT_MODEL_TOTAL_BITS) * prob;
	int bit;

	// Compare code and bound as if they were unsigned 32-bit integers.
	if ((this.code ^ 0x80000000) < (bound ^ 0x80000000)) {
	    this.range = bound;
	    probs[index] = (short) (prob + (RangeCoder.BIT_MODEL_TOTAL - prob >>> RangeCoder.MOVE_BITS));
	    bit = 0;
	} else {
	    this.range -= bound;
	    this.code -= bound;
	    probs[index] = (short) (prob - (prob >>> RangeCoder.MOVE_BITS));
	    bit = 1;
	}

	return bit;
    }

    public int decodeBitTree(final short[] probs) throws IOException {
	int symbol = 1;

	do {
	    symbol = symbol << 1 | this.decodeBit(probs, symbol);
	} while (symbol < probs.length);

	return symbol - probs.length;
    }

    public int decodeDirectBits(int count) throws IOException {
	int result = 0;

	do {
	    this.normalize();

	    this.range >>>= 1;
	    final int t = this.code - this.range >>> 31;
	    this.code -= this.range & t - 1;
	    result = result << 1 | 1 - t;
	} while (--count != 0);

	return result;
    }

    public int decodeReverseBitTree(final short[] probs) throws IOException {
	int symbol = 1;
	int i = 0;
	int result = 0;

	do {
	    final int bit = this.decodeBit(probs, symbol);
	    symbol = symbol << 1 | bit;
	    result |= bit << i++;
	} while (symbol < probs.length);

	return result;
    }

    public abstract void normalize() throws IOException;
}
