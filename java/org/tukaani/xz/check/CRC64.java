/*
 * CRC64
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.check;

public class CRC64 extends Check {
    private static final long poly = 0xC96C5795D7870F42L;
    private static final long[] crcTable = new long[256];

    static {
	for (int b = 0; b < CRC64.crcTable.length; ++b) {
	    long r = b;
	    for (int i = 0; i < 8; ++i) {
		if ((r & 1) == 1) {
		    r = r >>> 1 ^ CRC64.poly;
		} else {
		    r >>>= 1;
		}
	    }

	    CRC64.crcTable[b] = r;
	}
    }

    private long crc = -1;

    public CRC64() {
	this.size = 8;
	this.name = "CRC64";
    }

    @Override
    public byte[] finish() {
	final long value = ~this.crc;
	this.crc = -1;

	final byte[] buf = new byte[8];
	for (int i = 0; i < buf.length; ++i) {
	    buf[i] = (byte) (value >> i * 8);
	}

	return buf;
    }

    @Override
    public void update(final byte[] buf, int off, final int len) {
	final int end = off + len;

	while (off < end) {
	    this.crc = CRC64.crcTable[(buf[off++] ^ (int) this.crc) & 0xFF] ^ this.crc >>> 8;
	}
    }
}
