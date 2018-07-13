/*
 * LZMAEncoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lzma;

import java.io.IOException;

import org.tukaani.xz.lz.LZEncoder;
import org.tukaani.xz.lz.Matches;
import org.tukaani.xz.rangecoder.RangeEncoder;

public abstract class LZMAEncoder extends LZMACoder {
    class LengthEncoder extends LengthCoder {
	/**
	 * The prices are updated after at least
	 * <code>PRICE_UPDATE_INTERVAL</code> many lengths have been encoded
	 * with the same posState.
	 */
	private static final int PRICE_UPDATE_INTERVAL = 32; // FIXME?

	private final int[] counters;
	private final int[][] prices;

	LengthEncoder(final int pb, final int niceLen) {
	    final int posStates = 1 << pb;
	    this.counters = new int[posStates];

	    // Always allocate at least LOW_SYMBOLS + MID_SYMBOLS because
	    // it makes updatePrices slightly simpler. The prices aren't
	    // usually needed anyway if niceLen < 18.
	    final int lenSymbols = Math.max(niceLen - LZMACoder.MATCH_LEN_MIN + 1,
		    LengthCoder.LOW_SYMBOLS + LengthCoder.MID_SYMBOLS);
	    this.prices = new int[posStates][lenSymbols];
	}

	void encode(int len, final int posState) throws IOException {
	    len -= LZMACoder.MATCH_LEN_MIN;

	    if (len < LengthCoder.LOW_SYMBOLS) {
		LZMAEncoder.this.rc.encodeBit(this.choice, 0, 0);
		LZMAEncoder.this.rc.encodeBitTree(this.low[posState], len);
	    } else {
		LZMAEncoder.this.rc.encodeBit(this.choice, 0, 1);
		len -= LengthCoder.LOW_SYMBOLS;

		if (len < LengthCoder.MID_SYMBOLS) {
		    LZMAEncoder.this.rc.encodeBit(this.choice, 1, 0);
		    LZMAEncoder.this.rc.encodeBitTree(this.mid[posState], len);
		} else {
		    LZMAEncoder.this.rc.encodeBit(this.choice, 1, 1);
		    LZMAEncoder.this.rc.encodeBitTree(this.high, len - LengthCoder.MID_SYMBOLS);
		}
	    }

	    --this.counters[posState];
	}

	int getPrice(final int len, final int posState) {
	    return this.prices[posState][len - LZMACoder.MATCH_LEN_MIN];
	}

	@Override
	void reset() {
	    super.reset();

	    // Reset counters to zero to force price update before
	    // the prices are needed.
	    for (int i = 0; i < this.counters.length; ++i) {
		this.counters[i] = 0;
	    }
	}

	void updatePrices() {
	    for (int posState = 0; posState < this.counters.length; ++posState) {
		if (this.counters[posState] <= 0) {
		    this.counters[posState] = LengthEncoder.PRICE_UPDATE_INTERVAL;
		    this.updatePrices(posState);
		}
	    }
	}

	private void updatePrices(final int posState) {
	    int choice0Price = RangeEncoder.getBitPrice(this.choice[0], 0);

	    int i = 0;
	    for (; i < LengthCoder.LOW_SYMBOLS; ++i) {
		this.prices[posState][i] = choice0Price + RangeEncoder.getBitTreePrice(this.low[posState], i);
	    }

	    choice0Price = RangeEncoder.getBitPrice(this.choice[0], 1);
	    int choice1Price = RangeEncoder.getBitPrice(this.choice[1], 0);

	    for (; i < LengthCoder.LOW_SYMBOLS + LengthCoder.MID_SYMBOLS; ++i) {
		this.prices[posState][i] = choice0Price + choice1Price
			+ RangeEncoder.getBitTreePrice(this.mid[posState], i - LengthCoder.LOW_SYMBOLS);
	    }

	    choice1Price = RangeEncoder.getBitPrice(this.choice[1], 1);

	    for (; i < this.prices[posState].length; ++i) {
		this.prices[posState][i] = choice0Price + choice1Price + RangeEncoder.getBitTreePrice(this.high,
			i - LengthCoder.LOW_SYMBOLS - LengthCoder.MID_SYMBOLS);
	    }
	}
    }

    class LiteralEncoder extends LiteralCoder {
	private class LiteralSubencoder extends LiteralSubcoder {
	    void encode() throws IOException {
		int symbol = LZMAEncoder.this.lz.getByte(LZMAEncoder.this.readAhead) | 0x100;

		if (LZMAEncoder.this.state.isLiteral()) {
		    int subencoderIndex;
		    int bit;

		    do {
			subencoderIndex = symbol >>> 8;
			bit = symbol >>> 7 & 1;
			LZMAEncoder.this.rc.encodeBit(this.probs, subencoderIndex, bit);
			symbol <<= 1;
		    } while (symbol < 0x10000);

		} else {
		    int matchByte = LZMAEncoder.this.lz
			    .getByte(LZMAEncoder.this.reps[0] + 1 + LZMAEncoder.this.readAhead);
		    int offset = 0x100;
		    int subencoderIndex;
		    int matchBit;
		    int bit;

		    do {
			matchByte <<= 1;
			matchBit = matchByte & offset;
			subencoderIndex = offset + matchBit + (symbol >>> 8);
			bit = symbol >>> 7 & 1;
			LZMAEncoder.this.rc.encodeBit(this.probs, subencoderIndex, bit);
			symbol <<= 1;
			offset &= ~(matchByte ^ symbol);
		    } while (symbol < 0x10000);
		}

		LZMAEncoder.this.state.updateLiteral();
	    }

	    int getMatchedPrice(int symbol, int matchByte) {
		int price = 0;
		int offset = 0x100;
		int subencoderIndex;
		int matchBit;
		int bit;

		symbol |= 0x100;

		do {
		    matchByte <<= 1;
		    matchBit = matchByte & offset;
		    subencoderIndex = offset + matchBit + (symbol >>> 8);
		    bit = symbol >>> 7 & 1;
		    price += RangeEncoder.getBitPrice(this.probs[subencoderIndex], bit);
		    symbol <<= 1;
		    offset &= ~(matchByte ^ symbol);
		} while (symbol < 0x100 << 8);

		return price;
	    }

	    int getNormalPrice(int symbol) {
		int price = 0;
		int subencoderIndex;
		int bit;

		symbol |= 0x100;

		do {
		    subencoderIndex = symbol >>> 8;
		    bit = symbol >>> 7 & 1;
		    price += RangeEncoder.getBitPrice(this.probs[subencoderIndex], bit);
		    symbol <<= 1;
		} while (symbol < 0x100 << 8);

		return price;
	    }
	}

	private final LiteralSubencoder[] subencoders;

	LiteralEncoder(final int lc, final int lp) {
	    super(lc, lp);

	    this.subencoders = new LiteralSubencoder[1 << lc + lp];
	    for (int i = 0; i < this.subencoders.length; ++i) {
		this.subencoders[i] = new LiteralSubencoder();
	    }
	}

	void encode() throws IOException {
	    assert LZMAEncoder.this.readAhead >= 0;
	    final int i = this.getSubcoderIndex(LZMAEncoder.this.lz.getByte(1 + LZMAEncoder.this.readAhead),
		    LZMAEncoder.this.lz.getPos() - LZMAEncoder.this.readAhead);
	    this.subencoders[i].encode();
	}

	void encodeInit() throws IOException {
	    // When encoding the first byte of the stream, there is
	    // no previous byte in the dictionary so the encode function
	    // wouldn't work.
	    assert LZMAEncoder.this.readAhead >= 0;
	    this.subencoders[0].encode();
	}

	int getPrice(final int curByte, final int matchByte, final int prevByte, final int pos, final State state) {
	    int price = RangeEncoder.getBitPrice(LZMAEncoder.this.isMatch[state.get()][pos & LZMAEncoder.this.posMask],
		    0);

	    final int i = this.getSubcoderIndex(prevByte, pos);
	    price += state.isLiteral() ? this.subencoders[i].getNormalPrice(curByte)
		    : this.subencoders[i].getMatchedPrice(curByte, matchByte);

	    return price;
	}

	void reset() {
	    for (final LiteralSubencoder subencoder : this.subencoders) {
		subencoder.reset();
	    }
	}
    }

    public static final int MODE_FAST = 1;

    public static final int MODE_NORMAL = 2;

    /**
     * LZMA2 chunk is considered full when its uncompressed size exceeds
     * <code>LZMA2_UNCOMPRESSED_LIMIT</code>.
     * <p>
     * A compressed LZMA2 chunk can hold 2 MiB of uncompressed data. A single
     * LZMA symbol may indicate up to MATCH_LEN_MAX bytes of data, so the LZMA2
     * chunk is considered full when there is less space than MATCH_LEN_MAX
     * bytes.
     */
    private static final int LZMA2_UNCOMPRESSED_LIMIT = (2 << 20) - LZMACoder.MATCH_LEN_MAX;
    /**
     * LZMA2 chunk is considered full when its compressed size exceeds
     * <code>LZMA2_COMPRESSED_LIMIT</code>.
     * <p>
     * The maximum compressed size of a LZMA2 chunk is 64 KiB. A single LZMA
     * symbol might use 20 bytes of space even though it usually takes just one
     * byte or so. Two more bytes are needed for LZMA2 uncompressed chunks (see
     * LZMA2OutputStream.writeChunk). Leave a little safety margin and use 26
     * bytes.
     */
    private static final int LZMA2_COMPRESSED_LIMIT = (64 << 10) - 26;

    private static final int DIST_PRICE_UPDATE_INTERVAL = LZMACoder.FULL_DISTANCES;
    private static final int ALIGN_PRICE_UPDATE_INTERVAL = LZMACoder.ALIGN_SIZE;

    /**
     * Gets an integer [0, 63] matching the highest two bits of an integer. This
     * is like bit scan reverse (BSR) on x86 except that this also cares about
     * the second highest bit.
     */
    public static int getDistSlot(final int dist) {
	if (dist <= LZMACoder.DIST_MODEL_START && dist >= 0) {
	    return dist;
	}

	int n = dist;
	int i = 31;

	if ((n & 0xFFFF0000) == 0) {
	    n <<= 16;
	    i = 15;
	}

	if ((n & 0xFF000000) == 0) {
	    n <<= 8;
	    i -= 8;
	}

	if ((n & 0xF0000000) == 0) {
	    n <<= 4;
	    i -= 4;
	}

	if ((n & 0xC0000000) == 0) {
	    n <<= 2;
	    i -= 2;
	}

	if ((n & 0x80000000) == 0) {
	    --i;
	}

	return (i << 1) + (dist >>> i - 1 & 1);
    }

    public static LZMAEncoder getInstance(final RangeEncoder rc, final int lc, final int lp, final int pb,
	    final int mode, final int dictSize, final int extraSizeBefore, final int niceLen, final int mf,
	    final int depthLimit) {
	switch (mode) {
	case MODE_FAST:
	    return new LZMAEncoderFast(rc, lc, lp, pb, dictSize, extraSizeBefore, niceLen, mf, depthLimit);

	case MODE_NORMAL:
	    return new LZMAEncoderNormal(rc, lc, lp, pb, dictSize, extraSizeBefore, niceLen, mf, depthLimit);
	}

	throw new IllegalArgumentException();
    }

    public static int getMemoryUsage(final int mode, final int dictSize, final int extraSizeBefore, final int mf) {
	int m = 80;

	switch (mode) {
	case MODE_FAST:
	    m += LZMAEncoderFast.getMemoryUsage(dictSize, extraSizeBefore, mf);
	    break;

	case MODE_NORMAL:
	    m += LZMAEncoderNormal.getMemoryUsage(dictSize, extraSizeBefore, mf);
	    break;

	default:
	    throw new IllegalArgumentException();
	}

	return m;
    }

    private final RangeEncoder rc;

    final LZEncoder lz;
    final LiteralEncoder literalEncoder;

    final LengthEncoder matchLenEncoder;
    final LengthEncoder repLenEncoder;
    final int niceLen;
    private int distPriceCount = 0;

    private int alignPriceCount = 0;
    private final int distSlotPricesSize;
    private final int[][] distSlotPrices;

    private final int[][] fullDistPrices = new int[LZMACoder.DIST_STATES][LZMACoder.FULL_DISTANCES];

    private final int[] alignPrices = new int[LZMACoder.ALIGN_SIZE];

    int back = 0;

    int readAhead = -1;

    private int uncompressedSize = 0;

    LZMAEncoder(final RangeEncoder rc, final LZEncoder lz, final int lc, final int lp, final int pb, final int dictSize,
	    final int niceLen) {
	super(pb);
	this.rc = rc;
	this.lz = lz;
	this.niceLen = niceLen;

	this.literalEncoder = new LiteralEncoder(lc, lp);
	this.matchLenEncoder = new LengthEncoder(pb, niceLen);
	this.repLenEncoder = new LengthEncoder(pb, niceLen);

	this.distSlotPricesSize = LZMAEncoder.getDistSlot(dictSize - 1) + 1;
	this.distSlotPrices = new int[LZMACoder.DIST_STATES][this.distSlotPricesSize];

	this.reset();
    }

    /**
     * Compress for LZMA1.
     */
    public void encodeForLZMA1() throws IOException {
	if (!this.lz.isStarted() && !this.encodeInit()) {
	    return;
	}

	while (this.encodeSymbol()) {
	}
    }

    /**
     * Compresses for LZMA2.
     *
     * @return true if the LZMA2 chunk became full, false otherwise
     */
    public boolean encodeForLZMA2() {
	// LZMA2 uses RangeEncoderToBuffer so IOExceptions aren't possible.
	try {
	    if (!this.lz.isStarted() && !this.encodeInit()) {
		return false;
	    }

	    while (this.uncompressedSize <= LZMAEncoder.LZMA2_UNCOMPRESSED_LIMIT
		    && this.rc.getPendingSize() <= LZMAEncoder.LZMA2_COMPRESSED_LIMIT) {
		if (!this.encodeSymbol()) {
		    return false;
		}
	    }
	} catch (final IOException e) {
	    throw new Error();
	}

	return true;
    }

    private boolean encodeInit() throws IOException {
	assert this.readAhead == -1;
	if (!this.lz.hasEnoughData(0)) {
	    return false;
	}

	// The first symbol must be a literal unless using
	// a preset dictionary. This code isn't run if using
	// a preset dictionary.
	this.skip(1);
	this.rc.encodeBit(this.isMatch[this.state.get()], 0, 0);
	this.literalEncoder.encodeInit();

	--this.readAhead;
	assert this.readAhead == -1;

	++this.uncompressedSize;
	assert this.uncompressedSize == 1;

	return true;
    }

    public void encodeLZMA1EndMarker() throws IOException {
	// End of stream marker is encoded as a match with the maximum
	// possible distance. The length is ignored by the decoder,
	// but the minimum length has been used by the LZMA SDK.
	//
	// Distance is a 32-bit unsigned integer in LZMA.
	// With Java's signed int, UINT32_MAX becomes -1.
	final int posState = this.lz.getPos() - this.readAhead & this.posMask;
	this.rc.encodeBit(this.isMatch[this.state.get()], posState, 1);
	this.rc.encodeBit(this.isRep, this.state.get(), 0);
	this.encodeMatch(-1, LZMACoder.MATCH_LEN_MIN, posState);
    }

    private void encodeMatch(final int dist, final int len, final int posState) throws IOException {
	this.state.updateMatch();
	this.matchLenEncoder.encode(len, posState);

	final int distSlot = LZMAEncoder.getDistSlot(dist);
	this.rc.encodeBitTree(this.distSlots[LZMACoder.getDistState(len)], distSlot);

	if (distSlot >= LZMACoder.DIST_MODEL_START) {
	    final int footerBits = (distSlot >>> 1) - 1;
	    final int base = (2 | distSlot & 1) << footerBits;
	    final int distReduced = dist - base;

	    if (distSlot < LZMACoder.DIST_MODEL_END) {
		this.rc.encodeReverseBitTree(this.distSpecial[distSlot - LZMACoder.DIST_MODEL_START], distReduced);
	    } else {
		this.rc.encodeDirectBits(distReduced >>> LZMACoder.ALIGN_BITS, footerBits - LZMACoder.ALIGN_BITS);
		this.rc.encodeReverseBitTree(this.distAlign, distReduced & LZMACoder.ALIGN_MASK);
		--this.alignPriceCount;
	    }
	}

	this.reps[3] = this.reps[2];
	this.reps[2] = this.reps[1];
	this.reps[1] = this.reps[0];
	this.reps[0] = dist;

	--this.distPriceCount;
    }

    private void encodeRepMatch(final int rep, final int len, final int posState) throws IOException {
	if (rep == 0) {
	    this.rc.encodeBit(this.isRep0, this.state.get(), 0);
	    this.rc.encodeBit(this.isRep0Long[this.state.get()], posState, len == 1 ? 0 : 1);
	} else {
	    final int dist = this.reps[rep];
	    this.rc.encodeBit(this.isRep0, this.state.get(), 1);

	    if (rep == 1) {
		this.rc.encodeBit(this.isRep1, this.state.get(), 0);
	    } else {
		this.rc.encodeBit(this.isRep1, this.state.get(), 1);
		this.rc.encodeBit(this.isRep2, this.state.get(), rep - 2);

		if (rep == 3) {
		    this.reps[3] = this.reps[2];
		}

		this.reps[2] = this.reps[1];
	    }

	    this.reps[1] = this.reps[0];
	    this.reps[0] = dist;
	}

	if (len == 1) {
	    this.state.updateShortRep();
	} else {
	    this.repLenEncoder.encode(len, posState);
	    this.state.updateLongRep();
	}
    }

    private boolean encodeSymbol() throws IOException {
	if (!this.lz.hasEnoughData(this.readAhead + 1)) {
	    return false;
	}

	final int len = this.getNextSymbol();

	assert this.readAhead >= 0;
	final int posState = this.lz.getPos() - this.readAhead & this.posMask;

	if (this.back == -1) {
	    // Literal i.e. eight-bit byte
	    assert len == 1;
	    this.rc.encodeBit(this.isMatch[this.state.get()], posState, 0);
	    this.literalEncoder.encode();
	} else {
	    // Some type of match
	    this.rc.encodeBit(this.isMatch[this.state.get()], posState, 1);
	    if (this.back < LZMACoder.REPS) {
		// Repeated match i.e. the same distance
		// has been used earlier.
		assert this.lz.getMatchLen(-this.readAhead, this.reps[this.back], len) == len;
		this.rc.encodeBit(this.isRep, this.state.get(), 1);
		this.encodeRepMatch(this.back, len, posState);
	    } else {
		// Normal match
		assert this.lz.getMatchLen(-this.readAhead, this.back - LZMACoder.REPS, len) == len;
		this.rc.encodeBit(this.isRep, this.state.get(), 0);
		this.encodeMatch(this.back - LZMACoder.REPS, len, posState);
	    }
	}

	this.readAhead -= len;
	this.uncompressedSize += len;

	return true;
    }

    int getAnyMatchPrice(final State state, final int posState) {
	return RangeEncoder.getBitPrice(this.isMatch[state.get()][posState], 1);
    }

    int getAnyRepPrice(final int anyMatchPrice, final State state) {
	return anyMatchPrice + RangeEncoder.getBitPrice(this.isRep[state.get()], 1);
    }

    int getLongRepAndLenPrice(final int rep, final int len, final State state, final int posState) {
	final int anyMatchPrice = this.getAnyMatchPrice(state, posState);
	final int anyRepPrice = this.getAnyRepPrice(anyMatchPrice, state);
	final int longRepPrice = this.getLongRepPrice(anyRepPrice, rep, state, posState);
	return longRepPrice + this.repLenEncoder.getPrice(len, posState);
    }

    int getLongRepPrice(final int anyRepPrice, final int rep, final State state, final int posState) {
	int price = anyRepPrice;

	if (rep == 0) {
	    price += RangeEncoder.getBitPrice(this.isRep0[state.get()], 0)
		    + RangeEncoder.getBitPrice(this.isRep0Long[state.get()][posState], 1);
	} else {
	    price += RangeEncoder.getBitPrice(this.isRep0[state.get()], 1);

	    if (rep == 1) {
		price += RangeEncoder.getBitPrice(this.isRep1[state.get()], 0);
	    } else {
		price += RangeEncoder.getBitPrice(this.isRep1[state.get()], 1)
			+ RangeEncoder.getBitPrice(this.isRep2[state.get()], rep - 2);
	    }
	}

	return price;
    }

    public LZEncoder getLZEncoder() {
	return this.lz;
    }

    int getMatchAndLenPrice(final int normalMatchPrice, final int dist, final int len, final int posState) {
	int price = normalMatchPrice + this.matchLenEncoder.getPrice(len, posState);
	final int distState = LZMACoder.getDistState(len);

	if (dist < LZMACoder.FULL_DISTANCES) {
	    price += this.fullDistPrices[distState][dist];
	} else {
	    // Note that distSlotPrices includes also
	    // the price of direct bits.
	    final int distSlot = LZMAEncoder.getDistSlot(dist);
	    price += this.distSlotPrices[distState][distSlot] + this.alignPrices[dist & LZMACoder.ALIGN_MASK];
	}

	return price;
    }

    Matches getMatches() {
	++this.readAhead;
	final Matches matches = this.lz.getMatches();
	assert this.lz.verifyMatches(matches);
	return matches;
    }

    /**
     * Gets the next LZMA symbol.
     * <p>
     * There are three types of symbols: literal (a single byte), repeated
     * match, and normal match. The symbol is indicated by the return value and
     * by the variable <code>back</code>.
     * <p>
     * Literal: <code>back == -1</code> and return value is <code>1</code>. The
     * literal itself needs to be read from <code>lz</code> separately.
     * <p>
     * Repeated match: <code>back</code> is in the range [0, 3] and the return
     * value is the length of the repeated match.
     * <p>
     * Normal match: <code>back - REPS<code> (<code>back - 4</code>) is the
     * distance of the match and the return value is the length of the match.
     */
    abstract int getNextSymbol();

    int getNormalMatchPrice(final int anyMatchPrice, final State state) {
	return anyMatchPrice + RangeEncoder.getBitPrice(this.isRep[state.get()], 0);
    }

    int getShortRepPrice(final int anyRepPrice, final State state, final int posState) {
	return anyRepPrice + RangeEncoder.getBitPrice(this.isRep0[state.get()], 0)
		+ RangeEncoder.getBitPrice(this.isRep0Long[state.get()][posState], 0);
    }

    public int getUncompressedSize() {
	return this.uncompressedSize;
    }

    @Override
    public void reset() {
	super.reset();
	this.literalEncoder.reset();
	this.matchLenEncoder.reset();
	this.repLenEncoder.reset();
	this.distPriceCount = 0;
	this.alignPriceCount = 0;

	this.uncompressedSize += this.readAhead + 1;
	this.readAhead = -1;
    }

    public void resetUncompressedSize() {
	this.uncompressedSize = 0;
    }

    void skip(final int len) {
	this.readAhead += len;
	this.lz.skip(len);
    }

    private void updateAlignPrices() {
	this.alignPriceCount = LZMAEncoder.ALIGN_PRICE_UPDATE_INTERVAL;

	for (int i = 0; i < LZMACoder.ALIGN_SIZE; ++i) {
	    this.alignPrices[i] = RangeEncoder.getReverseBitTreePrice(this.distAlign, i);
	}
    }

    private void updateDistPrices() {
	this.distPriceCount = LZMAEncoder.DIST_PRICE_UPDATE_INTERVAL;

	for (int distState = 0; distState < LZMACoder.DIST_STATES; ++distState) {
	    for (int distSlot = 0; distSlot < this.distSlotPricesSize; ++distSlot) {
		this.distSlotPrices[distState][distSlot] = RangeEncoder.getBitTreePrice(this.distSlots[distState],
			distSlot);
	    }

	    for (int distSlot = LZMACoder.DIST_MODEL_END; distSlot < this.distSlotPricesSize; ++distSlot) {
		final int count = (distSlot >>> 1) - 1 - LZMACoder.ALIGN_BITS;
		this.distSlotPrices[distState][distSlot] += RangeEncoder.getDirectBitsPrice(count);
	    }

	    for (int dist = 0; dist < LZMACoder.DIST_MODEL_START; ++dist) {
		this.fullDistPrices[distState][dist] = this.distSlotPrices[distState][dist];
	    }
	}

	int dist = LZMACoder.DIST_MODEL_START;
	for (int distSlot = LZMACoder.DIST_MODEL_START; distSlot < LZMACoder.DIST_MODEL_END; ++distSlot) {
	    final int footerBits = (distSlot >>> 1) - 1;
	    final int base = (2 | distSlot & 1) << footerBits;

	    final int limit = this.distSpecial[distSlot - LZMACoder.DIST_MODEL_START].length;
	    for (int i = 0; i < limit; ++i) {
		final int distReduced = dist - base;
		final int price = RangeEncoder
			.getReverseBitTreePrice(this.distSpecial[distSlot - LZMACoder.DIST_MODEL_START], distReduced);

		for (int distState = 0; distState < LZMACoder.DIST_STATES; ++distState) {
		    this.fullDistPrices[distState][dist] = this.distSlotPrices[distState][distSlot] + price;
		}

		++dist;
	    }
	}

	assert dist == LZMACoder.FULL_DISTANCES;
    }

    /**
     * Updates the lookup tables used for calculating match distance and length
     * prices. The updating is skipped for performance reasons if the tables
     * haven't changed much since the previous update.
     */
    void updatePrices() {
	if (this.distPriceCount <= 0) {
	    this.updateDistPrices();
	}

	if (this.alignPriceCount <= 0) {
	    this.updateAlignPrices();
	}

	this.matchLenEncoder.updatePrices();
	this.repLenEncoder.updatePrices();
    }
}
