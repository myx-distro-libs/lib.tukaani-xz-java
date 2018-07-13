/*
 * SimpleOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.IOException;

import org.tukaani.xz.simple.SimpleFilter;

class SimpleOutputStream extends FinishableOutputStream {
    private static final int FILTER_BUF_SIZE = 4096;

    static int getMemoryUsage() {
	return 1 + SimpleOutputStream.FILTER_BUF_SIZE / 1024;
    }

    private FinishableOutputStream out;

    private final SimpleFilter simpleFilter;
    private final byte[] filterBuf = new byte[SimpleOutputStream.FILTER_BUF_SIZE];
    private int pos = 0;

    private int unfiltered = 0;
    private IOException exception = null;

    private boolean finished = false;

    private final byte[] tempBuf = new byte[1];

    SimpleOutputStream(final FinishableOutputStream out, final SimpleFilter simpleFilter) {
	if (out == null) {
	    throw new NullPointerException();
	}

	this.out = out;
	this.simpleFilter = simpleFilter;
    }

    @Override
    public void close() throws IOException {
	if (this.out != null) {
	    if (!this.finished) {
		// out.close() must be called even if writePending() fails.
		// writePending() saves the possible exception so we can
		// ignore exceptions here.
		try {
		    this.writePending();
		} catch (final IOException e) {
		}
	    }

	    try {
		this.out.close();
	    } catch (final IOException e) {
		// If there is an earlier exception, the exception
		// from out.close() is lost.
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
	    // If it fails, don't call out.finish().
	    this.writePending();

	    try {
		this.out.finish();
	    } catch (final IOException e) {
		this.exception = e;
		throw e;
	    }
	}
    }

    @Override
    public void flush() throws IOException {
	throw new UnsupportedOptionsException("Flushing is not supported");
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
	    throw new XZIOException("Stream finished or closed");
	}

	while (len > 0) {
	    // Copy more unfiltered data into filterBuf.
	    final int copySize = Math.min(len, SimpleOutputStream.FILTER_BUF_SIZE - (this.pos + this.unfiltered));
	    System.arraycopy(buf, off, this.filterBuf, this.pos + this.unfiltered, copySize);
	    off += copySize;
	    len -= copySize;
	    this.unfiltered += copySize;

	    // Filter the data in filterBuf.
	    final int filtered = this.simpleFilter.code(this.filterBuf, this.pos, this.unfiltered);
	    assert filtered <= this.unfiltered;
	    this.unfiltered -= filtered;

	    // Write out the filtered data.
	    try {
		this.out.write(this.filterBuf, this.pos, filtered);
	    } catch (final IOException e) {
		this.exception = e;
		throw e;
	    }

	    this.pos += filtered;

	    // If end of filterBuf was reached, move the pending unfiltered
	    // data to the beginning of the buffer so that more data can
	    // be copied into filterBuf on the next loop iteration.
	    if (this.pos + this.unfiltered == SimpleOutputStream.FILTER_BUF_SIZE) {
		System.arraycopy(this.filterBuf, this.pos, this.filterBuf, 0, this.unfiltered);
		this.pos = 0;
	    }
	}
    }

    @Override
    public void write(final int b) throws IOException {
	this.tempBuf[0] = (byte) b;
	this.write(this.tempBuf, 0, 1);
    }

    private void writePending() throws IOException {
	assert !this.finished;

	if (this.exception != null) {
	    throw this.exception;
	}

	try {
	    this.out.write(this.filterBuf, this.pos, this.unfiltered);
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}

	this.finished = true;
    }
}
