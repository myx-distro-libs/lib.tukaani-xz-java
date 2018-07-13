/*
 * EncoderUtil
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.common;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

public class EncoderUtil extends Util {
    public static void encodeVLI(final OutputStream out, long num) throws IOException {
	while (num >= 0x80) {
	    out.write((byte) (num | 0x80));
	    num >>>= 7;
	}

	out.write((byte) num);
    }

    public static void writeCRC32(final OutputStream out, final byte[] buf) throws IOException {
	final CRC32 crc32 = new CRC32();
	crc32.update(buf);
	final long value = crc32.getValue();

	for (int i = 0; i < 4; ++i) {
	    out.write((byte) (value >>> i * 8));
	}
    }
}
