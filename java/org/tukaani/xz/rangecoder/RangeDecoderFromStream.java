/*
 * RangeDecoderFromStream
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
import java.io.InputStream;

import org.tukaani.xz.CorruptedInputException;

public final class RangeDecoderFromStream extends RangeDecoder {
    private final DataInputStream inData;

    public RangeDecoderFromStream(final InputStream in) throws IOException {
	this.inData = new DataInputStream(in);

	if (this.inData.readUnsignedByte() != 0x00) {
	    throw new CorruptedInputException();
	}

	this.code = this.inData.readInt();
	this.range = 0xFFFFFFFF;
    }

    public boolean isFinished() {
	return this.code == 0;
    }

    @Override
    public void normalize() throws IOException {
	if ((this.range & RangeCoder.TOP_MASK) == 0) {
	    this.code = this.code << RangeCoder.SHIFT_BITS | this.inData.readUnsignedByte();
	    this.range <<= RangeCoder.SHIFT_BITS;
	}
    }
}
