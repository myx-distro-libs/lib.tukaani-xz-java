/*
 * RangeEncoderToBuffer
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.rangecoder;

import java.io.IOException;
import java.io.OutputStream;

public final class RangeEncoderToBuffer extends RangeEncoder {
    private final byte[] buf;
    private int bufPos;

    public RangeEncoderToBuffer(final int bufSize) {
	this.buf = new byte[bufSize];
	this.reset();
    }

    @Override
    public int finish() {
	// super.finish() cannot throw an IOException because writeByte()
	// provided in this file cannot throw an IOException.
	try {
	    super.finish();
	} catch (final IOException e) {
	    throw new Error();
	}

	return this.bufPos;
    }

    @Override
    public int getPendingSize() {
	// With LZMA2 it is known that cacheSize fits into an int.
	return this.bufPos + (int) this.cacheSize + 5 - 1;
    }

    @Override
    public void reset() {
	super.reset();
	this.bufPos = 0;
    }

    public void write(final OutputStream out) throws IOException {
	out.write(this.buf, 0, this.bufPos);
    }

    @Override
    void writeByte(final int b) {
	this.buf[this.bufPos++] = (byte) b;
    }
}
