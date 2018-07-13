/*
 * State
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lzma;

final class State {
    static final int STATES = 12;

    private static final int LIT_STATES = 7;

    private static final int LIT_LIT = 0;
    private static final int MATCH_LIT_LIT = 1;
    private static final int REP_LIT_LIT = 2;
    private static final int SHORTREP_LIT_LIT = 3;
    private static final int MATCH_LIT = 4;
    private static final int REP_LIT = 5;
    private static final int SHORTREP_LIT = 6;
    private static final int LIT_MATCH = 7;
    private static final int LIT_LONGREP = 8;
    private static final int LIT_SHORTREP = 9;
    private static final int NONLIT_MATCH = 10;
    private static final int NONLIT_REP = 11;

    private int state;

    State() {
    }

    State(final State other) {
	this.state = other.state;
    }

    int get() {
	return this.state;
    }

    boolean isLiteral() {
	return this.state < State.LIT_STATES;
    }

    void reset() {
	this.state = State.LIT_LIT;
    }

    void set(final State other) {
	this.state = other.state;
    }

    void updateLiteral() {
	if (this.state <= State.SHORTREP_LIT_LIT) {
	    this.state = State.LIT_LIT;
	} else if (this.state <= State.LIT_SHORTREP) {
	    this.state -= 3;
	} else {
	    this.state -= 6;
	}
    }

    void updateLongRep() {
	this.state = this.state < State.LIT_STATES ? State.LIT_LONGREP : State.NONLIT_REP;
    }

    void updateMatch() {
	this.state = this.state < State.LIT_STATES ? State.LIT_MATCH : State.NONLIT_MATCH;
    }

    void updateShortRep() {
	this.state = this.state < State.LIT_STATES ? State.LIT_SHORTREP : State.NONLIT_REP;
    }
}
