/*
 * RangeEncoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.rangecoder;

import java.io.IOException;

public abstract class RangeEncoder extends RangeCoder {
    private static final int MOVE_REDUCING_BITS = 4;
    private static final int BIT_PRICE_SHIFT_BITS = 4;

    private static final int[] prices = new int[RangeCoder.BIT_MODEL_TOTAL >>> RangeEncoder.MOVE_REDUCING_BITS];

    static {
	for (int i = (1 << RangeEncoder.MOVE_REDUCING_BITS)
		/ 2; i < RangeCoder.BIT_MODEL_TOTAL; i += 1 << RangeEncoder.MOVE_REDUCING_BITS) {
	    int w = i;
	    int bitCount = 0;

	    for (int j = 0; j < RangeEncoder.BIT_PRICE_SHIFT_BITS; ++j) {
		w *= w;
		bitCount <<= 1;

		while ((w & 0xFFFF0000) != 0) {
		    w >>>= 1;
		    ++bitCount;
		}
	    }

	    RangeEncoder.prices[i >> RangeEncoder.MOVE_REDUCING_BITS] = (RangeCoder.BIT_MODEL_TOTAL_BITS << RangeEncoder.BIT_PRICE_SHIFT_BITS)
		    - 15 - bitCount;
	}
    }

    public static int getBitPrice(final int prob, final int bit) {
	// NOTE: Unlike in encodeBit(), here bit must be 0 or 1.
	assert bit == 0 || bit == 1;
	return RangeEncoder.prices[(prob ^ -bit & RangeCoder.BIT_MODEL_TOTAL - 1) >>> RangeEncoder.MOVE_REDUCING_BITS];
    }

    public static int getBitTreePrice(final short[] probs, int symbol) {
	int price = 0;
	symbol |= probs.length;

	do {
	    final int bit = symbol & 1;
	    symbol >>>= 1;
	    price += RangeEncoder.getBitPrice(probs[symbol], bit);
	} while (symbol != 1);

	return price;
    }

    public static int getDirectBitsPrice(final int count) {
	return count << RangeEncoder.BIT_PRICE_SHIFT_BITS;
    }

    public static int getReverseBitTreePrice(final short[] probs, int symbol) {
	int price = 0;
	int index = 1;
	symbol |= probs.length;

	do {
	    final int bit = symbol & 1;
	    symbol >>>= 1;
	    price += RangeEncoder.getBitPrice(probs[index], bit);
	    index = index << 1 | bit;
	} while (symbol != 1);

	return price;
    }

    private long low;

    private int range;

    // NOTE: int is OK for LZMA2 because a compressed chunk
    // is not more than 64 KiB, but with LZMA1 there is no chunking
    // so in theory cacheSize can grow very big. To be very safe,
    // use long instead of int since this code is used for LZMA1 too.
    long cacheSize;

    private byte cache;

    public void encodeBit(final short[] probs, final int index, final int bit) throws IOException {
	final int prob = probs[index];
	final int bound = (this.range >>> RangeCoder.BIT_MODEL_TOTAL_BITS) * prob;

	// NOTE: Any non-zero value for bit is taken as 1.
	if (bit == 0) {
	    this.range = bound;
	    probs[index] = (short) (prob + (RangeCoder.BIT_MODEL_TOTAL - prob >>> RangeCoder.MOVE_BITS));
	} else {
	    this.low += bound & 0xFFFFFFFFL;
	    this.range -= bound;
	    probs[index] = (short) (prob - (prob >>> RangeCoder.MOVE_BITS));
	}

	if ((this.range & RangeCoder.TOP_MASK) == 0) {
	    this.range <<= RangeCoder.SHIFT_BITS;
	    this.shiftLow();
	}
    }

    public void encodeBitTree(final short[] probs, final int symbol) throws IOException {
	int index = 1;
	int mask = probs.length;

	do {
	    mask >>>= 1;
	    final int bit = symbol & mask;
	    this.encodeBit(probs, index, bit);

	    index <<= 1;
	    if (bit != 0) {
		index |= 1;
	    }

	} while (mask != 1);
    }

    public void encodeDirectBits(final int value, int count) throws IOException {
	do {
	    this.range >>>= 1;
	    this.low += this.range & 0 - (value >>> --count & 1);

	    if ((this.range & RangeCoder.TOP_MASK) == 0) {
		this.range <<= RangeCoder.SHIFT_BITS;
		this.shiftLow();
	    }
	} while (count != 0);
    }

    public void encodeReverseBitTree(final short[] probs, int symbol) throws IOException {
	int index = 1;
	symbol |= probs.length;

	do {
	    final int bit = symbol & 1;
	    symbol >>>= 1;
	    this.encodeBit(probs, index, bit);
	    index = index << 1 | bit;
	} while (symbol != 1);
    }

    public int finish() throws IOException {
	for (int i = 0; i < 5; ++i) {
	    this.shiftLow();
	}

	// RangeEncoderToBuffer.finish() needs a return value to tell
	// how big the finished buffer is. RangeEncoderToStream has no
	// buffer and thus no return value is needed. Here we use a dummy
	// value which can be overriden in RangeEncoderToBuffer.finish().
	return -1;
    }

    public int getPendingSize() {
	// This function is only needed by users of RangeEncoderToBuffer,
	// but providing a must-be-never-called version here makes
	// LZMAEncoder simpler.
	throw new Error();
    }

    public void reset() {
	this.low = 0;
	this.range = 0xFFFFFFFF;
	this.cache = 0x00;
	this.cacheSize = 1;
    }

    private void shiftLow() throws IOException {
	final int lowHi = (int) (this.low >>> 32);

	if (lowHi != 0 || this.low < 0xFF000000L) {
	    int temp = this.cache;

	    do {
		this.writeByte(temp + lowHi);
		temp = 0xFF;
	    } while (--this.cacheSize != 0);

	    this.cache = (byte) (this.low >>> 24);
	}

	++this.cacheSize;
	this.low = (this.low & 0x00FFFFFF) << 8;
    }

    abstract void writeByte(int b) throws IOException;
}
