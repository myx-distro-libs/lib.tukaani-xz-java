/*
 * BCJ filter for little endian ARM instructions
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.simple;

public final class ARM implements SimpleFilter {
    private final boolean isEncoder;
    private int pos;

    public ARM(final boolean isEncoder, final int startPos) {
	this.isEncoder = isEncoder;
	this.pos = startPos + 8;
    }

    @Override
    public int code(final byte[] buf, final int off, final int len) {
	final int end = off + len - 4;
	int i;

	for (i = off; i <= end; i += 4) {
	    if ((buf[i + 3] & 0xFF) == 0xEB) {
		int src = (buf[i + 2] & 0xFF) << 16 | (buf[i + 1] & 0xFF) << 8 | buf[i] & 0xFF;
		src <<= 2;

		int dest;
		if (this.isEncoder) {
		    dest = src + this.pos + i - off;
		} else {
		    dest = src - (this.pos + i - off);
		}

		dest >>>= 2;
		buf[i + 2] = (byte) (dest >>> 16);
		buf[i + 1] = (byte) (dest >>> 8);
		buf[i] = (byte) dest;
	    }
	}

	i -= off;
	this.pos += i;
	return i;
    }
}
