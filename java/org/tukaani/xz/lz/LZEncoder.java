/*
 * LZEncoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lz;

import java.io.IOException;
import java.io.OutputStream;

public abstract class LZEncoder {
    public static final int MF_HC4 = 0x04;
    public static final int MF_BT4 = 0x14;

    /**
     * Gets the size of the LZ window buffer that needs to be allocated.
     */
    private static int getBufSize(final int dictSize, final int extraSizeBefore, final int extraSizeAfter,
	    final int matchLenMax) {
	final int keepSizeBefore = extraSizeBefore + dictSize;
	final int keepSizeAfter = extraSizeAfter + matchLenMax;
	final int reserveSize = Math.min(dictSize / 2 + (256 << 10), 512 << 20);
	return keepSizeBefore + keepSizeAfter + reserveSize;
    }

    /**
     * Creates a new LZEncoder.
     * <p>
     * 
     * @param dictSize
     *            dictionary size
     *
     * @param extraSizeBefore
     *            number of bytes to keep available in the history in addition
     *            to dictSize
     *
     * @param extraSizeAfter
     *            number of bytes that must be available after current position
     *            + matchLenMax
     *
     * @param niceLen
     *            if a match of at least <code>niceLen</code> bytes is found, be
     *            happy with it and don't stop looking for longer matches
     *
     * @param matchLenMax
     *            don't test for matches longer than <code>matchLenMax</code>
     *            bytes
     *
     * @param mf
     *            match finder ID
     *
     * @param depthLimit
     *            match finder search depth limit
     */
    public static LZEncoder getInstance(final int dictSize, final int extraSizeBefore, final int extraSizeAfter,
	    final int niceLen, final int matchLenMax, final int mf, final int depthLimit) {
	switch (mf) {
	case MF_HC4:
	    return new HC4(dictSize, extraSizeBefore, extraSizeAfter, niceLen, matchLenMax, depthLimit);

	case MF_BT4:
	    return new BT4(dictSize, extraSizeBefore, extraSizeAfter, niceLen, matchLenMax, depthLimit);
	}

	throw new IllegalArgumentException();
    }

    /**
     * Gets approximate memory usage of the LZEncoder base structure and the
     * match finder as kibibytes.
     */
    public static int getMemoryUsage(final int dictSize, final int extraSizeBefore, final int extraSizeAfter,
	    final int matchLenMax, final int mf) {
	// Buffer size + a little extra
	int m = LZEncoder.getBufSize(dictSize, extraSizeBefore, extraSizeAfter, matchLenMax) / 1024 + 10;

	switch (mf) {
	case MF_HC4:
	    m += HC4.getMemoryUsage(dictSize);
	    break;

	case MF_BT4:
	    m += BT4.getMemoryUsage(dictSize);
	    break;

	default:
	    throw new IllegalArgumentException();
	}

	return m;
    }

    static void normalize(final int[] positions, final int normalizationOffset) {
	for (int i = 0; i < positions.length; ++i) {
	    if (positions[i] <= normalizationOffset) {
		positions[i] = 0;
	    } else {
		positions[i] -= normalizationOffset;
	    }
	}
    }

    /**
     * Number of bytes to keep available before the current byte when moving the
     * LZ window.
     */
    private final int keepSizeBefore;

    /**
     * Number of bytes that must be available, the current byte included, to
     * make hasEnoughData return true. Flushing and finishing are naturally
     * exceptions to this since there cannot be any data after the end of the
     * uncompressed input.
     */
    private final int keepSizeAfter;
    final int matchLenMax;
    final int niceLen;
    final byte[] buf;
    int readPos = -1;

    private int readLimit = -1;

    private boolean finishing = false;

    private int writePos = 0;

    private int pendingSize = 0;

    /**
     * Creates a new LZEncoder. See <code>getInstance</code>.
     */
    LZEncoder(final int dictSize, final int extraSizeBefore, final int extraSizeAfter, final int niceLen,
	    final int matchLenMax) {
	this.buf = new byte[LZEncoder.getBufSize(dictSize, extraSizeBefore, extraSizeAfter, matchLenMax)];

	this.keepSizeBefore = extraSizeBefore + dictSize;
	this.keepSizeAfter = extraSizeAfter + matchLenMax;

	this.matchLenMax = matchLenMax;
	this.niceLen = niceLen;
    }

    public void copyUncompressed(final OutputStream out, final int backward, final int len) throws IOException {
	out.write(this.buf, this.readPos + 1 - backward, len);
    }

    /**
     * Copies new data into the LZEncoder's buffer.
     */
    public int fillWindow(final byte[] in, final int off, int len) {
	assert !this.finishing;

	// Move the sliding window if needed.
	if (this.readPos >= this.buf.length - this.keepSizeAfter) {
	    this.moveWindow();
	}

	// Try to fill the dictionary buffer. If it becomes full,
	// some of the input bytes may be left unused.
	if (len > this.buf.length - this.writePos) {
	    len = this.buf.length - this.writePos;
	}

	System.arraycopy(in, off, this.buf, this.writePos, len);
	this.writePos += len;

	// Set the new readLimit but only if there's enough data to allow
	// encoding of at least one more byte.
	if (this.writePos >= this.keepSizeAfter) {
	    this.readLimit = this.writePos - this.keepSizeAfter;
	}

	this.processPendingBytes();

	// Tell the caller how much input we actually copied into
	// the dictionary.
	return len;
    }

    /**
     * Get the number of bytes available, including the current byte.
     * <p>
     * Note that the result is undefined if <code>getMatches</code> or
     * <code>skip</code> hasn't been called yet and no preset dictionary is
     * being used.
     */
    public int getAvail() {
	assert this.isStarted();
	return this.writePos - this.readPos;
    }

    /**
     * Gets the byte from the given backward offset.
     * <p>
     * The current byte is at <code>0</code>, the previous byte at
     * <code>1</code> etc. To get a byte at zero-based distance, use
     * <code>getByte(dist + 1)<code>.
     * <p>
     * This function is equivalent to <code>getByte(0, backward)</code>.
     */
    public int getByte(final int backward) {
	return this.buf[this.readPos - backward] & 0xFF;
    }

    /**
     * Gets the byte from the given forward minus backward offset. The forward
     * offset is added to the current position. This lets one read bytes ahead
     * of the current byte.
     */
    public int getByte(final int forward, final int backward) {
	return this.buf[this.readPos + forward - backward] & 0xFF;
    }

    /**
     * Runs match finder for the next byte and returns the matches found.
     */
    public abstract Matches getMatches();

    /**
     * Get the length of a match at the given distance.
     *
     * @param dist
     *            zero-based distance of the match to test
     * @param lenLimit
     *            don't test for a match longer than this
     *
     * @return length of the match; it is in the range [0, lenLimit]
     */
    public int getMatchLen(final int dist, final int lenLimit) {
	final int backPos = this.readPos - dist - 1;
	int len = 0;

	while (len < lenLimit && this.buf[this.readPos + len] == this.buf[backPos + len]) {
	    ++len;
	}

	return len;
    }

    /**
     * Get the length of a match at the given distance and forward offset.
     *
     * @param forward
     *            forward offset
     * @param dist
     *            zero-based distance of the match to test
     * @param lenLimit
     *            don't test for a match longer than this
     *
     * @return length of the match; it is in the range [0, lenLimit]
     */
    public int getMatchLen(final int forward, final int dist, final int lenLimit) {
	final int curPos = this.readPos + forward;
	final int backPos = curPos - dist - 1;
	int len = 0;

	while (len < lenLimit && this.buf[curPos + len] == this.buf[backPos + len]) {
	    ++len;
	}

	return len;
    }

    /**
     * Gets the lowest four bits of the absolute offset of the current byte.
     * Bits other than the lowest four are undefined.
     */
    public int getPos() {
	return this.readPos;
    }

    /**
     * Tests if there is enough input available to let the caller encode at
     * least one more byte.
     */
    public boolean hasEnoughData(final int alreadyReadLen) {
	return this.readPos - alreadyReadLen < this.readLimit;
    }

    /**
     * Returns true if at least one byte has already been run through the match
     * finder.
     */
    public boolean isStarted() {
	return this.readPos != -1;
    }

    /**
     * Moves to the next byte, checks if there is enough input available, and
     * returns the amount of input available.
     *
     * @param requiredForFlushing
     *            minimum number of available bytes when flushing; encoding may
     *            be continued with new input after flushing
     * @param requiredForFinishing
     *            minimum number of available bytes when finishing; encoding
     *            must not be continued after finishing or the match finder
     *            state may be corrupt
     *
     * @return the number of bytes available or zero if there is not enough
     *         input available
     */
    int movePos(final int requiredForFlushing, final int requiredForFinishing) {
	assert requiredForFlushing >= requiredForFinishing;

	++this.readPos;
	int avail = this.writePos - this.readPos;

	if (avail < requiredForFlushing) {
	    if (avail < requiredForFinishing || !this.finishing) {
		++this.pendingSize;
		avail = 0;
	    }
	}

	return avail;
    }

    /**
     * Moves data from the end of the buffer to the beginning, discarding old
     * data and making space for new input.
     */
    private void moveWindow() {
	// Align the move to a multiple of 16 bytes. LZMA2 needs this
	// because it uses the lowest bits from readPos to get the
	// alignment of the uncompressed data.
	final int moveOffset = this.readPos + 1 - this.keepSizeBefore & ~15;
	final int moveSize = this.writePos - moveOffset;
	System.arraycopy(this.buf, moveOffset, this.buf, 0, moveSize);

	this.readPos -= moveOffset;
	this.readLimit -= moveOffset;
	this.writePos -= moveOffset;
    }

    /**
     * Process pending bytes remaining from preset dictionary initialization or
     * encoder flush operation.
     */
    private void processPendingBytes() {
	// After flushing or setting a preset dictionary there will be
	// pending data that hasn't been ran through the match finder yet.
	// Run it through the match finder now if there is enough new data
	// available (readPos < readLimit) that the encoder may encode at
	// least one more input byte. This way we don't waste any time
	// looping in the match finder (and marking the same bytes as
	// pending again) if the application provides very little new data
	// per write call.
	if (this.pendingSize > 0 && this.readPos < this.readLimit) {
	    this.readPos -= this.pendingSize;
	    final int oldPendingSize = this.pendingSize;
	    this.pendingSize = 0;
	    this.skip(oldPendingSize);
	    assert this.pendingSize < oldPendingSize;
	}
    }

    /**
     * Marks that there is no more input remaining. The read position can be
     * advanced until the end of the data.
     */
    public void setFinishing() {
	this.readLimit = this.writePos - 1;
	this.finishing = true;
	this.processPendingBytes();
    }

    /**
     * Marks that all the input needs to be made available in the encoded
     * output.
     */
    public void setFlushing() {
	this.readLimit = this.writePos - 1;
	this.processPendingBytes();
    }

    /**
     * Sets a preset dictionary. If a preset dictionary is wanted, this function
     * must be called immediately after creating the LZEncoder before any data
     * has been encoded.
     */
    public void setPresetDict(final int dictSize, final byte[] presetDict) {
	assert !this.isStarted();
	assert this.writePos == 0;

	if (presetDict != null) {
	    // If the preset dictionary buffer is bigger than the dictionary
	    // size, copy only the tail of the preset dictionary.
	    final int copySize = Math.min(presetDict.length, dictSize);
	    final int offset = presetDict.length - copySize;
	    System.arraycopy(presetDict, offset, this.buf, 0, copySize);
	    this.writePos += copySize;
	    this.skip(copySize);
	}
    }

    /**
     * Skips the given number of bytes in the match finder.
     */
    public abstract void skip(int len);

    /**
     * Verifies that the matches returned by the match finder are valid. This is
     * meant to be used in an assert statement. This is totally useless for
     * actual encoding since match finder's results should naturally always be
     * valid if it isn't broken.
     *
     * @param matches
     *            return value from <code>getMatches</code>
     *
     * @return true if matches are valid, false if match finder is broken
     */
    public boolean verifyMatches(final Matches matches) {
	final int lenLimit = Math.min(this.getAvail(), this.matchLenMax);

	for (int i = 0; i < matches.count; ++i) {
	    if (this.getMatchLen(matches.dist[i], lenLimit) != matches.len[i]) {
		return false;
	    }
	}

	return true;
    }
}
