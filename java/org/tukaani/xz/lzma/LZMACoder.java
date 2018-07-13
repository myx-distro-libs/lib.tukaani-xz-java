/*
 * LZMACoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lzma;

import org.tukaani.xz.rangecoder.RangeCoder;

abstract class LZMACoder {
    abstract class LengthCoder {
	static final int LOW_SYMBOLS = 1 << 3;
	static final int MID_SYMBOLS = 1 << 3;
	static final int HIGH_SYMBOLS = 1 << 8;

	final short[] choice = new short[2];
	final short[][] low = new short[LZMACoder.POS_STATES_MAX][LengthCoder.LOW_SYMBOLS];
	final short[][] mid = new short[LZMACoder.POS_STATES_MAX][LengthCoder.MID_SYMBOLS];
	final short[] high = new short[LengthCoder.HIGH_SYMBOLS];

	void reset() {
	    RangeCoder.initProbs(this.choice);

	    for (final short[] element : this.low) {
		RangeCoder.initProbs(element);
	    }

	    for (int i = 0; i < this.low.length; ++i) {
		RangeCoder.initProbs(this.mid[i]);
	    }

	    RangeCoder.initProbs(this.high);
	}
    }

    abstract class LiteralCoder {
	abstract class LiteralSubcoder {
	    final short[] probs = new short[0x300];

	    void reset() {
		RangeCoder.initProbs(this.probs);
	    }
	}

	private final int lc;

	private final int literalPosMask;

	LiteralCoder(final int lc, final int lp) {
	    this.lc = lc;
	    this.literalPosMask = (1 << lp) - 1;
	}

	final int getSubcoderIndex(final int prevByte, final int pos) {
	    final int low = prevByte >> 8 - this.lc;
	    final int high = (pos & this.literalPosMask) << this.lc;
	    return low + high;
	}
    }

    static final int POS_STATES_MAX = 1 << 4;

    static final int MATCH_LEN_MIN = 2;
    static final int MATCH_LEN_MAX = LZMACoder.MATCH_LEN_MIN + LengthCoder.LOW_SYMBOLS + LengthCoder.MID_SYMBOLS
	    + LengthCoder.HIGH_SYMBOLS - 1;
    static final int DIST_STATES = 4;
    static final int DIST_SLOTS = 1 << 6;
    static final int DIST_MODEL_START = 4;

    static final int DIST_MODEL_END = 14;
    static final int FULL_DISTANCES = 1 << LZMACoder.DIST_MODEL_END / 2;
    static final int ALIGN_BITS = 4;

    static final int ALIGN_SIZE = 1 << LZMACoder.ALIGN_BITS;

    static final int ALIGN_MASK = LZMACoder.ALIGN_SIZE - 1;

    static final int REPS = 4;

    static final int getDistState(final int len) {
	return len < LZMACoder.DIST_STATES + LZMACoder.MATCH_LEN_MIN ? len - LZMACoder.MATCH_LEN_MIN
		: LZMACoder.DIST_STATES - 1;
    }

    final int posMask;
    final int[] reps = new int[LZMACoder.REPS];
    final State state = new State();
    final short[][] isMatch = new short[State.STATES][LZMACoder.POS_STATES_MAX];
    final short[] isRep = new short[State.STATES];
    final short[] isRep0 = new short[State.STATES];
    final short[] isRep1 = new short[State.STATES];
    final short[] isRep2 = new short[State.STATES];
    final short[][] isRep0Long = new short[State.STATES][LZMACoder.POS_STATES_MAX];

    final short[][] distSlots = new short[LZMACoder.DIST_STATES][LZMACoder.DIST_SLOTS];

    final short[][] distSpecial = { new short[2], new short[2], new short[4], new short[4], new short[8], new short[8],
	    new short[16], new short[16], new short[32], new short[32] };

    final short[] distAlign = new short[LZMACoder.ALIGN_SIZE];

    LZMACoder(final int pb) {
	this.posMask = (1 << pb) - 1;
    }

    void reset() {
	this.reps[0] = 0;
	this.reps[1] = 0;
	this.reps[2] = 0;
	this.reps[3] = 0;
	this.state.reset();

	for (final short[] element : this.isMatch) {
	    RangeCoder.initProbs(element);
	}

	RangeCoder.initProbs(this.isRep);
	RangeCoder.initProbs(this.isRep0);
	RangeCoder.initProbs(this.isRep1);
	RangeCoder.initProbs(this.isRep2);

	for (final short[] element : this.isRep0Long) {
	    RangeCoder.initProbs(element);
	}

	for (final short[] distSlot : this.distSlots) {
	    RangeCoder.initProbs(distSlot);
	}

	for (final short[] element : this.distSpecial) {
	    RangeCoder.initProbs(element);
	}

	RangeCoder.initProbs(this.distAlign);
    }
}
