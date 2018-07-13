/*
 * CountingOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Counts the number of bytes written to an output stream.
 * <p>
 * The <code>finish</code> method does nothing. This is
 * <code>FinishableOutputStream</code> instead of <code>OutputStream</code>
 * solely because it allows using this as the output stream for a chain of raw
 * filters.
 */
class CountingOutputStream extends FinishableOutputStream {
    private final OutputStream out;
    private long size = 0;

    public CountingOutputStream(final OutputStream out) {
	this.out = out;
    }

    @Override
    public void close() throws IOException {
	this.out.close();
    }

    @Override
    public void flush() throws IOException {
	this.out.flush();
    }

    public long getSize() {
	return this.size;
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
	this.out.write(b, off, len);
	if (this.size >= 0) {
	    this.size += len;
	}
    }

    @Override
    public void write(final int b) throws IOException {
	this.out.write(b);
	if (this.size >= 0) {
	    ++this.size;
	}
    }
}
