/*
 * LZDecoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lz;

import java.io.DataInputStream;
import java.io.IOException;

import org.tukaani.xz.CorruptedInputException;

public final class LZDecoder {
    private final byte[] buf;
    private int start = 0;
    private int pos = 0;
    private int full = 0;
    private int limit = 0;
    private int pendingLen = 0;
    private int pendingDist = 0;

    public LZDecoder(final int dictSize, final byte[] presetDict) {
	this.buf = new byte[dictSize];

	if (presetDict != null) {
	    this.pos = Math.min(presetDict.length, dictSize);
	    this.full = this.pos;
	    this.start = this.pos;
	    System.arraycopy(presetDict, presetDict.length - this.pos, this.buf, 0, this.pos);
	}
    }

    public void copyUncompressed(final DataInputStream inData, final int len) throws IOException {
	final int copySize = Math.min(this.buf.length - this.pos, len);
	inData.readFully(this.buf, this.pos, copySize);
	this.pos += copySize;

	if (this.full < this.pos) {
	    this.full = this.pos;
	}
    }

    public int flush(final byte[] out, final int outOff) {
	final int copySize = this.pos - this.start;
	if (this.pos == this.buf.length) {
	    this.pos = 0;
	}

	System.arraycopy(this.buf, this.start, out, outOff, copySize);
	this.start = this.pos;

	return copySize;
    }

    public int getByte(final int dist) {
	int offset = this.pos - dist - 1;
	if (dist >= this.pos) {
	    offset += this.buf.length;
	}

	return this.buf[offset] & 0xFF;
    }

    public int getPos() {
	return this.pos;
    }

    public boolean hasPending() {
	return this.pendingLen > 0;
    }

    public boolean hasSpace() {
	return this.pos < this.limit;
    }

    public void putByte(final byte b) {
	this.buf[this.pos++] = b;

	if (this.full < this.pos) {
	    this.full = this.pos;
	}
    }

    public void repeat(final int dist, final int len) throws IOException {
	if (dist < 0 || dist >= this.full) {
	    throw new CorruptedInputException();
	}

	int left = Math.min(this.limit - this.pos, len);
	this.pendingLen = len - left;
	this.pendingDist = dist;

	int back = this.pos - dist - 1;
	if (dist >= this.pos) {
	    back += this.buf.length;
	}

	do {
	    this.buf[this.pos++] = this.buf[back++];
	    if (back == this.buf.length) {
		back = 0;
	    }
	} while (--left > 0);

	if (this.full < this.pos) {
	    this.full = this.pos;
	}
    }

    public void repeatPending() throws IOException {
	if (this.pendingLen > 0) {
	    this.repeat(this.pendingDist, this.pendingLen);
	}
    }

    public void reset() {
	this.start = 0;
	this.pos = 0;
	this.full = 0;
	this.limit = 0;
	this.buf[this.buf.length - 1] = 0x00;
    }

    public void setLimit(final int outMax) {
	if (this.buf.length - this.pos <= outMax) {
	    this.limit = this.buf.length;
	} else {
	    this.limit = this.pos + outMax;
	}
    }
}
