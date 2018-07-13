/*
 * BCJ filter for Itanium (IA-64) instructions
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.simple;

public final class IA64 implements SimpleFilter {
    private static final int[] BRANCH_TABLE = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 6, 6, 0, 0, 7, 7,
	    4, 4, 0, 0, 4, 4, 0, 0 };

    private final boolean isEncoder;
    private int pos;

    public IA64(final boolean isEncoder, final int startPos) {
	this.isEncoder = isEncoder;
	this.pos = startPos;
    }

    @Override
    public int code(final byte[] buf, final int off, final int len) {
	final int end = off + len - 16;
	int i;

	for (i = off; i <= end; i += 16) {
	    final int instrTemplate = buf[i] & 0x1F;
	    final int mask = IA64.BRANCH_TABLE[instrTemplate];

	    for (int slot = 0, bitPos = 5; slot < 3; ++slot, bitPos += 41) {
		if ((mask >>> slot & 1) == 0) {
		    continue;
		}

		final int bytePos = bitPos >>> 3;
		final int bitRes = bitPos & 7;

		long instr = 0;
		for (int j = 0; j < 6; ++j) {
		    instr |= (buf[i + bytePos + j] & 0xFFL) << 8 * j;
		}

		long instrNorm = instr >>> bitRes;

		if ((instrNorm >>> 37 & 0x0F) != 0x05 || (instrNorm >>> 9 & 0x07) != 0x00) {
		    continue;
		}

		int src = (int) (instrNorm >>> 13 & 0x0FFFFF);
		src |= ((int) (instrNorm >>> 36) & 1) << 20;
		src <<= 4;

		int dest;
		if (this.isEncoder) {
		    dest = src + this.pos + i - off;
		} else {
		    dest = src - (this.pos + i - off);
		}

		dest >>>= 4;

		instrNorm &= ~(0x8FFFFFL << 13);
		instrNorm |= (dest & 0x0FFFFFL) << 13;
		instrNorm |= (dest & 0x100000L) << 36 - 20;

		instr &= (1 << bitRes) - 1;
		instr |= instrNorm << bitRes;

		for (int j = 0; j < 6; ++j) {
		    buf[i + bytePos + j] = (byte) (instr >>> 8 * j);
		}
	    }
	}

	i -= off;
	this.pos += i;
	return i;
    }
}
