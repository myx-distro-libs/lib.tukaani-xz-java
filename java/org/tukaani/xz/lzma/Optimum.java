/*
 * Optimum
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lzma;

final class Optimum {
    private static final int INFINITY_PRICE = 1 << 30;

    final State state = new State();
    final int[] reps = new int[LZMACoder.REPS];

    /**
     * Cumulative price of arriving to this byte.
     */
    int price;

    int optPrev;
    int backPrev;
    boolean prev1IsLiteral;

    boolean hasPrev2;
    int optPrev2;
    int backPrev2;

    /**
     * Resets the price.
     */
    void reset() {
	this.price = Optimum.INFINITY_PRICE;
    }

    /**
     * Sets to indicate one LZMA symbol (literal, rep, or match).
     */
    void set1(final int newPrice, final int optCur, final int back) {
	this.price = newPrice;
	this.optPrev = optCur;
	this.backPrev = back;
	this.prev1IsLiteral = false;
    }

    /**
     * Sets to indicate two LZMA symbols of which the first one is a literal.
     */
    void set2(final int newPrice, final int optCur, final int back) {
	this.price = newPrice;
	this.optPrev = optCur + 1;
	this.backPrev = back;
	this.prev1IsLiteral = true;
	this.hasPrev2 = false;
    }

    /**
     * Sets to indicate three LZMA symbols of which the second one is a literal.
     */
    void set3(final int newPrice, final int optCur, final int back2, final int len2, final int back) {
	this.price = newPrice;
	this.optPrev = optCur + len2 + 1;
	this.backPrev = back;
	this.prev1IsLiteral = true;
	this.hasPrev2 = true;
	this.optPrev2 = optCur;
	this.backPrev2 = back2;
    }
}
