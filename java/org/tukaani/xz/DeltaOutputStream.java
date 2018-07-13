/*
 * DeltaOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.IOException;

import org.tukaani.xz.delta.DeltaEncoder;

class DeltaOutputStream extends FinishableOutputStream {
    private static final int FILTER_BUF_SIZE = 4096;

    static int getMemoryUsage() {
	return 1 + DeltaOutputStream.FILTER_BUF_SIZE / 1024;
    }

    private FinishableOutputStream out;
    private final DeltaEncoder delta;

    private final byte[] filterBuf = new byte[DeltaOutputStream.FILTER_BUF_SIZE];
    private boolean finished = false;

    private IOException exception = null;

    private final byte[] tempBuf = new byte[1];

    DeltaOutputStream(final FinishableOutputStream out, final DeltaOptions options) {
	this.out = out;
	this.delta = new DeltaEncoder(options.getDistance());
    }

    @Override
    public void close() throws IOException {
	if (this.out != null) {
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
	    if (this.exception != null) {
		throw this.exception;
	    }

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
	    this.out.flush();
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}
    }

    @Override
    public void write(final byte[] buf, int off, int len) throws IOException {
	if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) {
	    throw new IndexOutOfBoundsException();
	}

	if (this.exception != null) {
	    throw this.exception;
	}

	if (this.finished) {
	    throw new XZIOException("Stream finished");
	}

	try {
	    while (len > DeltaOutputStream.FILTER_BUF_SIZE) {
		this.delta.encode(buf, off, DeltaOutputStream.FILTER_BUF_SIZE, this.filterBuf);
		this.out.write(this.filterBuf);
		off += DeltaOutputStream.FILTER_BUF_SIZE;
		len -= DeltaOutputStream.FILTER_BUF_SIZE;
	    }

	    this.delta.encode(buf, off, len, this.filterBuf);
	    this.out.write(this.filterBuf, 0, len);
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
}
