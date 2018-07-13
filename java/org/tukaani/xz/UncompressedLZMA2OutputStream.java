/*
 * UncompressedLZMA2OutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.DataOutputStream;
import java.io.IOException;

class UncompressedLZMA2OutputStream extends FinishableOutputStream {
    static int getMemoryUsage() {
	// uncompBuf + a little extra
	return 70;
    }

    private FinishableOutputStream out;

    private final DataOutputStream outData;
    private final byte[] uncompBuf = new byte[LZMA2OutputStream.COMPRESSED_SIZE_MAX];
    private int uncompPos = 0;

    private boolean dictResetNeeded = true;
    private boolean finished = false;

    private IOException exception = null;

    private final byte[] tempBuf = new byte[1];

    UncompressedLZMA2OutputStream(final FinishableOutputStream out) {
	if (out == null) {
	    throw new NullPointerException();
	}

	this.out = out;
	this.outData = new DataOutputStream(out);
    }

    @Override
    public void close() throws IOException {
	if (this.out != null) {
	    if (!this.finished) {
		try {
		    this.writeEndMarker();
		} catch (final IOException e) {
		}
	    }

	    try {
		this.out.close();
	    } catch (final IOException e) {
		if (this.exception == null) {
		    this.exception = e;
		}
	    }

	    this.out = null;
	}

	if (this.exception != null) {
	    throw this.exception;
	}
    }

    @Override
    public void finish() throws IOException {
	if (!this.finished) {
	    this.writeEndMarker();

	    try {
		this.out.finish();
	    } catch (final IOException e) {
		this.exception = e;
		throw e;
	    }

	    this.finished = true;
	}
    }

    @Override
    public void flush() throws IOException {
	if (this.exception != null) {
	    throw this.exception;
	}

	if (this.finished) {
	    throw new XZIOException("Stream finished or closed");
	}

	try {
	    if (this.uncompPos > 0) {
		this.writeChunk();
	    }

	    this.out.flush();
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}
    }

    @Override
    public void write(final byte[] buf, final int off, int len) throws IOException {
	if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) {
	    throw new IndexOutOfBoundsException();
	}

	if (this.exception != null) {
	    throw this.exception;
	}

	if (this.finished) {
	    throw new XZIOException("Stream finished or closed");
	}

	try {
	    while (len > 0) {
		final int copySize = Math.min(this.uncompBuf.length - this.uncompPos, len);
		System.arraycopy(buf, off, this.uncompBuf, this.uncompPos, copySize);
		len -= copySize;
		this.uncompPos += copySize;

		if (this.uncompPos == this.uncompBuf.length) {
		    this.writeChunk();
		}
	    }
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}
    }

    @Override
    public void write(final int b) throws IOException {
	this.tempBuf[0] = (byte) b;
	this.write(this.tempBuf, 0, 1);
    }

    private void writeChunk() throws IOException {
	this.outData.writeByte(this.dictResetNeeded ? 0x01 : 0x02);
	this.outData.writeShort(this.uncompPos - 1);
	this.outData.write(this.uncompBuf, 0, this.uncompPos);
	this.uncompPos = 0;
	this.dictResetNeeded = false;
    }

    private void writeEndMarker() throws IOException {
	if (this.exception != null) {
	    throw this.exception;
	}

	if (this.finished) {
	    throw new XZIOException("Stream finished or closed");
	}

	try {
	    if (this.uncompPos > 0) {
		this.writeChunk();
	    }

	    this.out.write(0x00);
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}
    }
}
