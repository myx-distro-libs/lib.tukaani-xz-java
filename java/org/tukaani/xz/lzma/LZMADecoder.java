/*
 * LZMADecoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lzma;

import java.io.IOException;

import org.tukaani.xz.lz.LZDecoder;
import org.tukaani.xz.rangecoder.RangeDecoder;

public final class LZMADecoder extends LZMACoder {
    private class LengthDecoder extends LengthCoder {
	int decode(final int posState) throws IOException {
	    if (LZMADecoder.this.rc.decodeBit(this.choice, 0) == 0) {
		return LZMADecoder.this.rc.decodeBitTree(this.low[posState]) + LZMACoder.MATCH_LEN_MIN;
	    }

	    if (LZMADecoder.this.rc.decodeBit(this.choice, 1) == 0) {
		return LZMADecoder.this.rc.decodeBitTree(this.mid[posState]) + LZMACoder.MATCH_LEN_MIN
			+ LengthCoder.LOW_SYMBOLS;
	    }

	    return LZMADecoder.this.rc.decodeBitTree(this.high) + LZMACoder.MATCH_LEN_MIN + LengthCoder.LOW_SYMBOLS
		    + LengthCoder.MID_SYMBOLS;
	}
    }

    private class LiteralDecoder extends LiteralCoder {
	private class LiteralSubdecoder extends LiteralSubcoder {
	    void decode() throws IOException {
		int symbol = 1;

		if (LZMADecoder.this.state.isLiteral()) {
		    do {
			symbol = symbol << 1 | LZMADecoder.this.rc.decodeBit(this.probs, symbol);
		    } while (symbol < 0x100);

		} else {
		    int matchByte = LZMADecoder.this.lz.getByte(LZMADecoder.this.reps[0]);
		    int offset = 0x100;
		    int matchBit;
		    int bit;

		    do {
			matchByte <<= 1;
			matchBit = matchByte & offset;
			bit = LZMADecoder.this.rc.decodeBit(this.probs, offset + matchBit + symbol);
			symbol = symbol << 1 | bit;
			offset &= 0 - bit ^ ~matchBit;
		    } while (symbol < 0x100);
		}

		LZMADecoder.this.lz.putByte((byte) symbol);
		LZMADecoder.this.state.updateLiteral();
	    }
	}

	private final LiteralSubdecoder[] subdecoders;

	LiteralDecoder(final int lc, final int lp) {
	    super(lc, lp);

	    this.subdecoders = new LiteralSubdecoder[1 << lc + lp];
	    for (int i = 0; i < this.subdecoders.length; ++i) {
		this.subdecoders[i] = new LiteralSubdecoder();
	    }
	}

	void decode() throws IOException {
	    final int i = this.getSubcoderIndex(LZMADecoder.this.lz.getByte(0), LZMADecoder.this.lz.getPos());
	    this.subdecoders[i].decode();
	}

	void reset() {
	    for (final LiteralSubdecoder subdecoder : this.subdecoders) {
		subdecoder.reset();
	    }
	}
    }

    private final LZDecoder lz;
    private final RangeDecoder rc;
    private final LiteralDecoder literalDecoder;

    private final LengthDecoder matchLenDecoder = new LengthDecoder();

    private final LengthDecoder repLenDecoder = new LengthDecoder();

    public LZMADecoder(final LZDecoder lz, final RangeDecoder rc, final int lc, final int lp, final int pb) {
	super(pb);
	this.lz = lz;
	this.rc = rc;
	this.literalDecoder = new LiteralDecoder(lc, lp);
	this.reset();
    }

    public void decode() throws IOException {
	this.lz.repeatPending();

	while (this.lz.hasSpace()) {
	    final int posState = this.lz.getPos() & this.posMask;

	    if (this.rc.decodeBit(this.isMatch[this.state.get()], posState) == 0) {
		this.literalDecoder.decode();
	    } else {
		final int len = this.rc.decodeBit(this.isRep, this.state.get()) == 0 ? this.decodeMatch(posState)
			: this.decodeRepMatch(posState);

		// NOTE: With LZMA1 streams that have the end marker,
		// this will throw CorruptedInputException. LZMAInputStream
		// handles it specially.
		this.lz.repeat(this.reps[0], len);
	    }
	}

	this.rc.normalize();
    }

    private int decodeMatch(final int posState) throws IOException {
	this.state.updateMatch();

	this.reps[3] = this.reps[2];
	this.reps[2] = this.reps[1];
	this.reps[1] = this.reps[0];

	final int len = this.matchLenDecoder.decode(posState);
	final int distSlot = this.rc.decodeBitTree(this.distSlots[LZMACoder.getDistState(len)]);

	if (distSlot < LZMACoder.DIST_MODEL_START) {
	    this.reps[0] = distSlot;
	} else {
	    final int limit = (distSlot >> 1) - 1;
	    this.reps[0] = (2 | distSlot & 1) << limit;

	    if (distSlot < LZMACoder.DIST_MODEL_END) {
		this.reps[0] |= this.rc.decodeReverseBitTree(this.distSpecial[distSlot - LZMACoder.DIST_MODEL_START]);
	    } else {
		this.reps[0] |= this.rc.decodeDirectBits(limit - LZMACoder.ALIGN_BITS) << LZMACoder.ALIGN_BITS;
		this.reps[0] |= this.rc.decodeReverseBitTree(this.distAlign);
	    }
	}

	return len;
    }

    private int decodeRepMatch(final int posState) throws IOException {
	if (this.rc.decodeBit(this.isRep0, this.state.get()) == 0) {
	    if (this.rc.decodeBit(this.isRep0Long[this.state.get()], posState) == 0) {
		this.state.updateShortRep();
		return 1;
	    }
	} else {
	    int tmp;

	    if (this.rc.decodeBit(this.isRep1, this.state.get()) == 0) {
		tmp = this.reps[1];
	    } else {
		if (this.rc.decodeBit(this.isRep2, this.state.get()) == 0) {
		    tmp = this.reps[2];
		} else {
		    tmp = this.reps[3];
		    this.reps[3] = this.reps[2];
		}

		this.reps[2] = this.reps[1];
	    }

	    this.reps[1] = this.reps[0];
	    this.reps[0] = tmp;
	}

	this.state.updateLongRep();

	return this.repLenDecoder.decode(posState);
    }

    /**
     * Returns true if LZMA end marker was detected. It is encoded as the
     * maximum match distance which with signed ints becomes -1. This function
     * is needed only for LZMA1. LZMA2 doesn't use the end marker in the LZMA
     * layer.
     */
    public boolean endMarkerDetected() {
	return this.reps[0] == -1;
    }

    @Override
    public void reset() {
	super.reset();
	this.literalDecoder.reset();
	this.matchLenDecoder.reset();
	this.repLenDecoder.reset();
    }
}
