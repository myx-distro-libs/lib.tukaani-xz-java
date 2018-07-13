/*
 * SimpleInputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.IOException;
import java.io.InputStream;

import org.tukaani.xz.simple.SimpleFilter;

class SimpleInputStream extends InputStream {
    private static final int FILTER_BUF_SIZE = 4096;

    static int getMemoryUsage() {
	return 1 + SimpleInputStream.FILTER_BUF_SIZE / 1024;
    }

    private InputStream in;

    private final SimpleFilter simpleFilter;
    private final byte[] filterBuf = new byte[SimpleInputStream.FILTER_BUF_SIZE];
    private int pos = 0;
    private int filtered = 0;

    private int unfiltered = 0;
    private boolean endReached = false;

    private IOException exception = null;

    private final byte[] tempBuf = new byte[1];

    SimpleInputStream(final InputStream in, final SimpleFilter simpleFilter) {
	// Check for null because otherwise null isn't detect
	// in this constructor.
	if (in == null) {
	    throw new NullPointerException();
	}

	// The simpleFilter argument comes from this package
	// so it is known to be non-null already.
	assert simpleFilter != null;

	this.in = in;
	this.simpleFilter = simpleFilter;
    }

    @Override
    public int available() throws IOException {
	if (this.in == null) {
	    throw new XZIOException("Stream closed");
	}

	if (this.exception != null) {
	    throw this.exception;
	}

	return this.filtered;
    }

    @Override
    public void close() throws IOException {
	if (this.in != null) {
	    try {
		this.in.close();
	    } finally {
		this.in = null;
	    }
	}
    }

    @Override
    public int read() throws IOException {
	return this.read(this.tempBuf, 0, 1) == -1 ? -1 : this.tempBuf[0] & 0xFF;
    }

    @Override
    public int read(final byte[] buf, int off, int len) throws IOException {
	if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) {
	    throw new IndexOutOfBoundsException();
	}

	if (len == 0) {
	    return 0;
	}

	if (this.in == null) {
	    throw new XZIOException("Stream closed");
	}

	if (this.exception != null) {
	    throw this.exception;
	}

	try {
	    int size = 0;

	    while (true) {
		// Copy filtered data into the caller-provided buffer.
		final int copySize = Math.min(this.filtered, len);
		System.arraycopy(this.filterBuf, this.pos, buf, off, copySize);
		this.pos += copySize;
		this.filtered -= copySize;
		off += copySize;
		len -= copySize;
		size += copySize;

		// If end of filterBuf was reached, move the pending data to
		// the beginning of the buffer so that more data can be
		// copied into filterBuf on the next loop iteration.
		if (this.pos + this.filtered + this.unfiltered == SimpleInputStream.FILTER_BUF_SIZE) {
		    System.arraycopy(this.filterBuf, this.pos, this.filterBuf, 0, this.filtered + this.unfiltered);
		    this.pos = 0;
		}

		if (len == 0 || this.endReached) {
		    return size > 0 ? size : -1;
		}

		assert this.filtered == 0;

		// Get more data into the temporary buffer.
		int inSize = SimpleInputStream.FILTER_BUF_SIZE - (this.pos + this.filtered + this.unfiltered);
		inSize = this.in.read(this.filterBuf, this.pos + this.filtered + this.unfiltered, inSize);

		if (inSize == -1) {
		    // Mark the remaining unfiltered bytes to be ready
		    // to be copied out.
		    this.endReached = true;
		    this.filtered = this.unfiltered;
		    this.unfiltered = 0;
		} else {
		    // Filter the data in filterBuf.
		    this.unfiltered += inSize;
		    this.filtered = this.simpleFilter.code(this.filterBuf, this.pos, this.unfiltered);
		    assert this.filtered <= this.unfiltered;
		    this.unfiltered -= this.filtered;
		}
	    }
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}
    }
}
