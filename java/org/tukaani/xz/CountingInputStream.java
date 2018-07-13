/*
 * CountingInputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counts the number of bytes read from an input stream.
 */
class CountingInputStream extends FilterInputStream {
    private long size = 0;

    public CountingInputStream(final InputStream in) {
	super(in);
    }

    public long getSize() {
	return this.size;
    }

    @Override
    public int read() throws IOException {
	final int ret = this.in.read();
	if (ret != -1 && this.size >= 0) {
	    ++this.size;
	}

	return ret;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
	final int ret = this.in.read(b, off, len);
	if (ret > 0 && this.size >= 0) {
	    this.size += ret;
	}

	return ret;
    }
}
