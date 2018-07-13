/*
 * BCJ filter for x86 instructions
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.simple;

public final class X86 implements SimpleFilter {
    private static final boolean[] MASK_TO_ALLOWED_STATUS = { true, true, true, false, true, false, false, false };

    private static final int[] MASK_TO_BIT_NUMBER = { 0, 1, 2, 2, 3, 3, 3, 3 };

    private static boolean test86MSByte(final byte b) {
	final int i = b & 0xFF;
	return i == 0x00 || i == 0xFF;
    }

    private final boolean isEncoder;
    private int pos;

    private int prevMask = 0;

    public X86(final boolean isEncoder, final int startPos) {
	this.isEncoder = isEncoder;
	this.pos = startPos + 5;
    }

    @Override
    public int code(final byte[] buf, final int off, final int len) {
	int prevPos = off - 1;
	final int end = off + len - 5;
	int i;

	for (i = off; i <= end; ++i) {
	    if ((buf[i] & 0xFE) != 0xE8) {
		continue;
	    }

	    prevPos = i - prevPos;
	    if ((prevPos & ~3) != 0) { // (unsigned)prevPos > 3
		this.prevMask = 0;
	    } else {
		this.prevMask = this.prevMask << prevPos - 1 & 7;
		if (this.prevMask != 0) {
		    if (!X86.MASK_TO_ALLOWED_STATUS[this.prevMask]
			    || X86.test86MSByte(buf[i + 4 - X86.MASK_TO_BIT_NUMBER[this.prevMask]])) {
			prevPos = i;
			this.prevMask = this.prevMask << 1 | 1;
			continue;
		    }
		}
	    }

	    prevPos = i;

	    if (X86.test86MSByte(buf[i + 4])) {
		int src = buf[i + 1] & 0xFF | (buf[i + 2] & 0xFF) << 8 | (buf[i + 3] & 0xFF) << 16
			| (buf[i + 4] & 0xFF) << 24;
		int dest;
		while (true) {
		    if (this.isEncoder) {
			dest = src + this.pos + i - off;
		    } else {
			dest = src - (this.pos + i - off);
		    }

		    if (this.prevMask == 0) {
			break;
		    }

		    final int index = X86.MASK_TO_BIT_NUMBER[this.prevMask] * 8;
		    if (!X86.test86MSByte((byte) (dest >>> 24 - index))) {
			break;
		    }

		    src = dest ^ (1 << 32 - index) - 1;
		}

		buf[i + 1] = (byte) dest;
		buf[i + 2] = (byte) (dest >>> 8);
		buf[i + 3] = (byte) (dest >>> 16);
		buf[i + 4] = (byte) ~((dest >>> 24 & 1) - 1);
		i += 4;
	    } else {
		this.prevMask = this.prevMask << 1 | 1;
	    }
	}

	prevPos = i - prevPos;
	this.prevMask = (prevPos & ~3) != 0 ? 0 : this.prevMask << prevPos - 1;

	i -= off;
	this.pos += i;
	return i;
    }
}
