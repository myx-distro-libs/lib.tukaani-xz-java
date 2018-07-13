/*
 * RangeDecoderFromBuffer
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.rangecoder;

import java.io.DataInputStream;
import java.io.IOException;

import org.tukaani.xz.CorruptedInputException;

public final class RangeDecoderFromBuffer extends RangeDecoder {
    private static final int INIT_SIZE = 5;

    private final byte[] buf;
    private int pos = 0;
    private int end = 0;

    public RangeDecoderFromBuffer(final int inputSizeMax) {
	this.buf = new byte[inputSizeMax - RangeDecoderFromBuffer.INIT_SIZE];
    }

    public boolean isFinished() {
	return this.pos == this.end && this.code == 0;
    }

    public boolean isInBufferOK() {
	return this.pos <= this.end;
    }

    @Override
    public void normalize() throws IOException {
	if ((this.range & RangeCoder.TOP_MASK) == 0) {
	    try {
		// If the input is corrupt, this might throw
		// ArrayIndexOutOfBoundsException.
		this.code = this.code << RangeCoder.SHIFT_BITS | this.buf[this.pos++] & 0xFF;
		this.range <<= RangeCoder.SHIFT_BITS;
	    } catch (final ArrayIndexOutOfBoundsException e) {
		throw new CorruptedInputException();
	    }
	}
    }

    public void prepareInputBuffer(final DataInputStream in, final int len) throws IOException {
	if (len < RangeDecoderFromBuffer.INIT_SIZE) {
	    throw new CorruptedInputException();
	}

	if (in.readUnsignedByte() != 0x00) {
	    throw new CorruptedInputException();
	}

	this.code = in.readInt();
	this.range = 0xFFFFFFFF;

	this.pos = 0;
	this.end = len - RangeDecoderFromBuffer.INIT_SIZE;
	in.readFully(this.buf, 0, this.end);
    }
}
