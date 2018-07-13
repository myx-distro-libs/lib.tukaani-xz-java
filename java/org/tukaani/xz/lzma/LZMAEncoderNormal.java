/*
 * LZMAEncoderNormal
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

final class LZMAEncoderNormal extends LZMAEncoder {
    private static final int OPTS = 4096;

    private static final int EXTRA_SIZE_BEFORE = LZMAEncoderNormal.OPTS;
    private static final int EXTRA_SIZE_AFTER = LZMAEncoderNormal.OPTS;

    static int getMemoryUsage(final int dictSize, final int extraSizeBefore, final int mf) {
	return LZEncoder.getMemoryUsage(dictSize, Math.max(extraSizeBefore, LZMAEncoderNormal.EXTRA_SIZE_BEFORE),
		LZMAEncoderNormal.EXTRA_SIZE_AFTER, LZMACoder.MATCH_LEN_MAX, mf) + LZMAEncoderNormal.OPTS * 64 / 1024;
    }

    private final Optimum[] opts = new Optimum[LZMAEncoderNormal.OPTS];
    private int optCur = 0;

    private int optEnd = 0;

    private Matches matches;
    // These are fields solely to avoid allocating the objects again and
    // again on each function call.
    private final int[] repLens = new int[LZMACoder.REPS];

    private final State nextState = new State();

    LZMAEncoderNormal(final RangeEncoder rc, final int lc, final int lp, final int pb, final int dictSize,
	    final int extraSizeBefore, final int niceLen, final int mf, final int depthLimit) {
	super(rc,
		LZEncoder.getInstance(dictSize, Math.max(extraSizeBefore, LZMAEncoderNormal.EXTRA_SIZE_BEFORE),
			LZMAEncoderNormal.EXTRA_SIZE_AFTER, niceLen, LZMACoder.MATCH_LEN_MAX, mf, depthLimit),
		lc, lp, pb, dictSize, niceLen);

	for (int i = 0; i < LZMAEncoderNormal.OPTS; ++i) {
	    this.opts[i] = new Optimum();
	}
    }

    /**
     * Calculates prices of a literal, a short rep, and literal + rep0.
     */
    private void calc1BytePrices(final int pos, final int posState, final int avail, final int anyRepPrice) {
	// This will be set to true if using a literal or a short rep.
	boolean nextIsByte = false;

	final int curByte = this.lz.getByte(0);
	final int matchByte = this.lz.getByte(this.opts[this.optCur].reps[0] + 1);

	// Try a literal.
	final int literalPrice = this.opts[this.optCur].price + this.literalEncoder.getPrice(curByte, matchByte,
		this.lz.getByte(1), pos, this.opts[this.optCur].state);
	if (literalPrice < this.opts[this.optCur + 1].price) {
	    this.opts[this.optCur + 1].set1(literalPrice, this.optCur, -1);
	    nextIsByte = true;
	}

	// Try a short rep.
	if (matchByte == curByte
		&& (this.opts[this.optCur + 1].optPrev == this.optCur || this.opts[this.optCur + 1].backPrev != 0)) {
	    final int shortRepPrice = this.getShortRepPrice(anyRepPrice, this.opts[this.optCur].state, posState);
	    if (shortRepPrice <= this.opts[this.optCur + 1].price) {
		this.opts[this.optCur + 1].set1(shortRepPrice, this.optCur, 0);
		nextIsByte = true;
	    }
	}

	// If neither a literal nor a short rep was the cheapest choice,
	// try literal + long rep0.
	if (!nextIsByte && matchByte != curByte && avail > LZMACoder.MATCH_LEN_MIN) {
	    final int lenLimit = Math.min(this.niceLen, avail - 1);
	    final int len = this.lz.getMatchLen(1, this.opts[this.optCur].reps[0], lenLimit);

	    if (len >= LZMACoder.MATCH_LEN_MIN) {
		this.nextState.set(this.opts[this.optCur].state);
		this.nextState.updateLiteral();
		final int nextPosState = pos + 1 & this.posMask;
		final int price = literalPrice + this.getLongRepAndLenPrice(0, len, this.nextState, nextPosState);

		final int i = this.optCur + 1 + len;
		while (this.optEnd < i) {
		    this.opts[++this.optEnd].reset();
		}

		if (price < this.opts[i].price) {
		    this.opts[i].set2(price, this.optCur, 0);
		}
	    }
	}
    }

    /**
     * Calculates prices of long rep and long rep + literal + rep0.
     */
    private int calcLongRepPrices(final int pos, final int posState, final int avail, final int anyRepPrice) {
	int startLen = LZMACoder.MATCH_LEN_MIN;
	final int lenLimit = Math.min(avail, this.niceLen);

	for (int rep = 0; rep < LZMACoder.REPS; ++rep) {
	    final int len = this.lz.getMatchLen(this.opts[this.optCur].reps[rep], lenLimit);
	    if (len < LZMACoder.MATCH_LEN_MIN) {
		continue;
	    }

	    while (this.optEnd < this.optCur + len) {
		this.opts[++this.optEnd].reset();
	    }

	    final int longRepPrice = this.getLongRepPrice(anyRepPrice, rep, this.opts[this.optCur].state, posState);

	    for (int i = len; i >= LZMACoder.MATCH_LEN_MIN; --i) {
		final int price = longRepPrice + this.repLenEncoder.getPrice(i, posState);
		if (price < this.opts[this.optCur + i].price) {
		    this.opts[this.optCur + i].set1(price, this.optCur, rep);
		}
	    }

	    if (rep == 0) {
		startLen = len + 1;
	    }

	    final int len2Limit = Math.min(this.niceLen, avail - len - 1);
	    final int len2 = this.lz.getMatchLen(len + 1, this.opts[this.optCur].reps[rep], len2Limit);

	    if (len2 >= LZMACoder.MATCH_LEN_MIN) {
		// Rep
		int price = longRepPrice + this.repLenEncoder.getPrice(len, posState);
		this.nextState.set(this.opts[this.optCur].state);
		this.nextState.updateLongRep();

		// Literal
		final int curByte = this.lz.getByte(len, 0);
		final int matchByte = this.lz.getByte(0); // lz.getByte(len,
							  // len)
		final int prevByte = this.lz.getByte(len, 1);
		price += this.literalEncoder.getPrice(curByte, matchByte, prevByte, pos + len, this.nextState);
		this.nextState.updateLiteral();

		// Rep0
		final int nextPosState = pos + len + 1 & this.posMask;
		price += this.getLongRepAndLenPrice(0, len2, this.nextState, nextPosState);

		final int i = this.optCur + len + 1 + len2;
		while (this.optEnd < i) {
		    this.opts[++this.optEnd].reset();
		}

		if (price < this.opts[i].price) {
		    this.opts[i].set3(price, this.optCur, rep, len, 0);
		}
	    }
	}

	return startLen;
    }

    /**
     * Calculates prices of a normal match and normal match + literal + rep0.
     */
    private void calcNormalMatchPrices(final int pos, final int posState, final int avail, final int anyMatchPrice,
	    final int startLen) {
	// If the longest match is so long that it would not fit into
	// the opts array, shorten the matches.
	if (this.matches.len[this.matches.count - 1] > avail) {
	    this.matches.count = 0;
	    while (this.matches.len[this.matches.count] < avail) {
		++this.matches.count;
	    }

	    this.matches.len[this.matches.count++] = avail;
	}

	if (this.matches.len[this.matches.count - 1] < startLen) {
	    return;
	}

	while (this.optEnd < this.optCur + this.matches.len[this.matches.count - 1]) {
	    this.opts[++this.optEnd].reset();
	}

	final int normalMatchPrice = this.getNormalMatchPrice(anyMatchPrice, this.opts[this.optCur].state);

	int match = 0;
	while (startLen > this.matches.len[match]) {
	    ++match;
	}

	for (int len = startLen;; ++len) {
	    final int dist = this.matches.dist[match];

	    // Calculate the price of a match of len bytes from the nearest
	    // possible distance.
	    final int matchAndLenPrice = this.getMatchAndLenPrice(normalMatchPrice, dist, len, posState);
	    if (matchAndLenPrice < this.opts[this.optCur + len].price) {
		this.opts[this.optCur + len].set1(matchAndLenPrice, this.optCur, dist + LZMACoder.REPS);
	    }

	    if (len != this.matches.len[match]) {
		continue;
	    }

	    // Try match + literal + rep0. First get the length of the rep0.
	    final int len2Limit = Math.min(this.niceLen, avail - len - 1);
	    final int len2 = this.lz.getMatchLen(len + 1, dist, len2Limit);

	    if (len2 >= LZMACoder.MATCH_LEN_MIN) {
		this.nextState.set(this.opts[this.optCur].state);
		this.nextState.updateMatch();

		// Literal
		final int curByte = this.lz.getByte(len, 0);
		final int matchByte = this.lz.getByte(0); // lz.getByte(len,
							  // len)
		final int prevByte = this.lz.getByte(len, 1);
		int price = matchAndLenPrice
			+ this.literalEncoder.getPrice(curByte, matchByte, prevByte, pos + len, this.nextState);
		this.nextState.updateLiteral();

		// Rep0
		final int nextPosState = pos + len + 1 & this.posMask;
		price += this.getLongRepAndLenPrice(0, len2, this.nextState, nextPosState);

		final int i = this.optCur + len + 1 + len2;
		while (this.optEnd < i) {
		    this.opts[++this.optEnd].reset();
		}

		if (price < this.opts[i].price) {
		    this.opts[i].set3(price, this.optCur, dist + LZMACoder.REPS, len, 0);
		}
	    }

	    if (++match == this.matches.count) {
		break;
	    }
	}
    }

    /**
     * Converts the opts array from backward indexes to forward indexes. Then it
     * will be simple to get the next symbol from the array in later calls to
     * <code>getNextSymbol()</code>.
     */
    private int convertOpts() {
	this.optEnd = this.optCur;

	int optPrev = this.opts[this.optCur].optPrev;

	do {
	    final Optimum opt = this.opts[this.optCur];

	    if (opt.prev1IsLiteral) {
		this.opts[optPrev].optPrev = this.optCur;
		this.opts[optPrev].backPrev = -1;
		this.optCur = optPrev--;

		if (opt.hasPrev2) {
		    this.opts[optPrev].optPrev = optPrev + 1;
		    this.opts[optPrev].backPrev = opt.backPrev2;
		    this.optCur = optPrev;
		    optPrev = opt.optPrev2;
		}
	    }

	    final int temp = this.opts[optPrev].optPrev;
	    this.opts[optPrev].optPrev = this.optCur;
	    this.optCur = optPrev;
	    optPrev = temp;
	} while (this.optCur > 0);

	this.optCur = this.opts[0].optPrev;
	this.back = this.opts[this.optCur].backPrev;
	return this.optCur;
    }

    @Override
    int getNextSymbol() {
	// If there are pending symbols from an earlier call to this
	// function, return those symbols first.
	if (this.optCur < this.optEnd) {
	    final int len = this.opts[this.optCur].optPrev - this.optCur;
	    this.optCur = this.opts[this.optCur].optPrev;
	    this.back = this.opts[this.optCur].backPrev;
	    return len;
	}

	assert this.optCur == this.optEnd;
	this.optCur = 0;
	this.optEnd = 0;
	this.back = -1;

	if (this.readAhead == -1) {
	    this.matches = this.getMatches();
	}

	// Get the number of bytes available in the dictionary, but
	// not more than the maximum match length. If there aren't
	// enough bytes remaining to encode a match at all, return
	// immediately to encode this byte as a literal.
	int avail = Math.min(this.lz.getAvail(), LZMACoder.MATCH_LEN_MAX);
	if (avail < LZMACoder.MATCH_LEN_MIN) {
	    return 1;
	}

	// Get the lengths of repeated matches.
	int repBest = 0;
	for (int rep = 0; rep < LZMACoder.REPS; ++rep) {
	    this.repLens[rep] = this.lz.getMatchLen(this.reps[rep], avail);

	    if (this.repLens[rep] < LZMACoder.MATCH_LEN_MIN) {
		this.repLens[rep] = 0;
		continue;
	    }

	    if (this.repLens[rep] > this.repLens[repBest]) {
		repBest = rep;
	    }
	}

	// Return if the best repeated match is at least niceLen bytes long.
	if (this.repLens[repBest] >= this.niceLen) {
	    this.back = repBest;
	    this.skip(this.repLens[repBest] - 1);
	    return this.repLens[repBest];
	}

	// Initialize mainLen and mainDist to the longest match found
	// by the match finder.
	int mainLen = 0;
	int mainDist = 0;
	if (this.matches.count > 0) {
	    mainLen = this.matches.len[this.matches.count - 1];
	    mainDist = this.matches.dist[this.matches.count - 1];

	    // Return if it is at least niceLen bytes long.
	    if (mainLen >= this.niceLen) {
		this.back = mainDist + LZMACoder.REPS;
		this.skip(mainLen - 1);
		return mainLen;
	    }
	}

	final int curByte = this.lz.getByte(0);
	final int matchByte = this.lz.getByte(this.reps[0] + 1);

	// If the match finder found no matches and this byte cannot be
	// encoded as a repeated match (short or long), we must be return
	// to have the byte encoded as a literal.
	if (mainLen < LZMACoder.MATCH_LEN_MIN && curByte != matchByte
		&& this.repLens[repBest] < LZMACoder.MATCH_LEN_MIN) {
	    return 1;
	}

	int pos = this.lz.getPos();
	int posState = pos & this.posMask;

	// Calculate the price of encoding the current byte as a literal.
	{
	    final int prevByte = this.lz.getByte(1);
	    final int literalPrice = this.literalEncoder.getPrice(curByte, matchByte, prevByte, pos, this.state);
	    this.opts[1].set1(literalPrice, 0, -1);
	}

	int anyMatchPrice = this.getAnyMatchPrice(this.state, posState);
	int anyRepPrice = this.getAnyRepPrice(anyMatchPrice, this.state);

	// If it is possible to encode this byte as a short rep, see if
	// it is cheaper than encoding it as a literal.
	if (matchByte == curByte) {
	    final int shortRepPrice = this.getShortRepPrice(anyRepPrice, this.state, posState);
	    if (shortRepPrice < this.opts[1].price) {
		this.opts[1].set1(shortRepPrice, 0, 0);
	    }
	}

	// Return if there is neither normal nor long repeated match. Use
	// a short match instead of a literal if is is possible and cheaper.
	this.optEnd = Math.max(mainLen, this.repLens[repBest]);
	if (this.optEnd < LZMACoder.MATCH_LEN_MIN) {
	    assert this.optEnd == 0 : this.optEnd;
	    this.back = this.opts[1].backPrev;
	    return 1;
	}

	// Update the lookup tables for distances and lengths before using
	// those price calculation functions. (The price function above
	// don't need these tables.)
	this.updatePrices();

	// Initialize the state and reps of this position in opts[].
	// updateOptStateAndReps() will need these to get the new
	// state and reps for the next byte.
	this.opts[0].state.set(this.state);
	System.arraycopy(this.reps, 0, this.opts[0].reps, 0, LZMACoder.REPS);

	// Initialize the prices for latter opts that will be used below.
	for (int i = this.optEnd; i >= LZMACoder.MATCH_LEN_MIN; --i) {
	    this.opts[i].reset();
	}

	// Calculate the prices of repeated matches of all lengths.
	for (int rep = 0; rep < LZMACoder.REPS; ++rep) {
	    int repLen = this.repLens[rep];
	    if (repLen < LZMACoder.MATCH_LEN_MIN) {
		continue;
	    }

	    final int longRepPrice = this.getLongRepPrice(anyRepPrice, rep, this.state, posState);
	    do {
		final int price = longRepPrice + this.repLenEncoder.getPrice(repLen, posState);
		if (price < this.opts[repLen].price) {
		    this.opts[repLen].set1(price, 0, rep);
		}
	    } while (--repLen >= LZMACoder.MATCH_LEN_MIN);
	}

	// Calculate the prices of normal matches that are longer than rep0.
	{
	    int len = Math.max(this.repLens[0] + 1, LZMACoder.MATCH_LEN_MIN);
	    if (len <= mainLen) {
		final int normalMatchPrice = this.getNormalMatchPrice(anyMatchPrice, this.state);

		// Set i to the index of the shortest match that is
		// at least len bytes long.
		int i = 0;
		while (len > this.matches.len[i]) {
		    ++i;
		}

		while (true) {
		    final int dist = this.matches.dist[i];
		    final int price = this.getMatchAndLenPrice(normalMatchPrice, dist, len, posState);
		    if (price < this.opts[len].price) {
			this.opts[len].set1(price, 0, dist + LZMACoder.REPS);
		    }

		    if (len == this.matches.len[i]) {
			if (++i == this.matches.count) {
			    break;
			}
		    }

		    ++len;
		}
	    }
	}

	avail = Math.min(this.lz.getAvail(), LZMAEncoderNormal.OPTS - 1);

	// Get matches for later bytes and optimize the use of LZMA symbols
	// by calculating the prices and picking the cheapest symbol
	// combinations.
	while (++this.optCur < this.optEnd) {
	    this.matches = this.getMatches();
	    if (this.matches.count > 0 && this.matches.len[this.matches.count - 1] >= this.niceLen) {
		break;
	    }

	    --avail;
	    ++pos;
	    posState = pos & this.posMask;

	    this.updateOptStateAndReps();
	    anyMatchPrice = this.opts[this.optCur].price
		    + this.getAnyMatchPrice(this.opts[this.optCur].state, posState);
	    anyRepPrice = this.getAnyRepPrice(anyMatchPrice, this.opts[this.optCur].state);

	    this.calc1BytePrices(pos, posState, avail, anyRepPrice);

	    if (avail >= LZMACoder.MATCH_LEN_MIN) {
		final int startLen = this.calcLongRepPrices(pos, posState, avail, anyRepPrice);
		if (this.matches.count > 0) {
		    this.calcNormalMatchPrices(pos, posState, avail, anyMatchPrice, startLen);
		}
	    }
	}

	return this.convertOpts();
    }

    @Override
    public void reset() {
	this.optCur = 0;
	this.optEnd = 0;
	super.reset();
    }

    /**
     * Updates the state and reps for the current byte in the opts array.
     */
    private void updateOptStateAndReps() {
	int optPrev = this.opts[this.optCur].optPrev;
	assert optPrev < this.optCur;

	if (this.opts[this.optCur].prev1IsLiteral) {
	    --optPrev;

	    if (this.opts[this.optCur].hasPrev2) {
		this.opts[this.optCur].state.set(this.opts[this.opts[this.optCur].optPrev2].state);
		if (this.opts[this.optCur].backPrev2 < LZMACoder.REPS) {
		    this.opts[this.optCur].state.updateLongRep();
		} else {
		    this.opts[this.optCur].state.updateMatch();
		}
	    } else {
		this.opts[this.optCur].state.set(this.opts[optPrev].state);
	    }

	    this.opts[this.optCur].state.updateLiteral();
	} else {
	    this.opts[this.optCur].state.set(this.opts[optPrev].state);
	}

	if (optPrev == this.optCur - 1) {
	    // Must be either a short rep or a literal.
	    assert this.opts[this.optCur].backPrev == 0 || this.opts[this.optCur].backPrev == -1;

	    if (this.opts[this.optCur].backPrev == 0) {
		this.opts[this.optCur].state.updateShortRep();
	    } else {
		this.opts[this.optCur].state.updateLiteral();
	    }

	    System.arraycopy(this.opts[optPrev].reps, 0, this.opts[this.optCur].reps, 0, LZMACoder.REPS);
	} else {
	    int back;
	    if (this.opts[this.optCur].prev1IsLiteral && this.opts[this.optCur].hasPrev2) {
		optPrev = this.opts[this.optCur].optPrev2;
		back = this.opts[this.optCur].backPrev2;
		this.opts[this.optCur].state.updateLongRep();
	    } else {
		back = this.opts[this.optCur].backPrev;
		if (back < LZMACoder.REPS) {
		    this.opts[this.optCur].state.updateLongRep();
		} else {
		    this.opts[this.optCur].state.updateMatch();
		}
	    }

	    if (back < LZMACoder.REPS) {
		this.opts[this.optCur].reps[0] = this.opts[optPrev].reps[back];

		int rep;
		for (rep = 1; rep <= back; ++rep) {
		    this.opts[this.optCur].reps[rep] = this.opts[optPrev].reps[rep - 1];
		}

		for (; rep < LZMACoder.REPS; ++rep) {
		    this.opts[this.optCur].reps[rep] = this.opts[optPrev].reps[rep];
		}
	    } else {
		this.opts[this.optCur].reps[0] = back - LZMACoder.REPS;
		System.arraycopy(this.opts[optPrev].reps, 0, this.opts[this.optCur].reps, 1, LZMACoder.REPS - 1);
	    }
	}
    }
}
