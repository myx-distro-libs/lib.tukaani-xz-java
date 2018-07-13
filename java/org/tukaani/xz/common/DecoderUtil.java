/*
 * DecoderUtil
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.common;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

import org.tukaani.xz.CorruptedInputException;
import org.tukaani.xz.UnsupportedOptionsException;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZFormatException;

public class DecoderUtil extends Util {
    public static boolean areStreamFlagsEqual(final StreamFlags a, final StreamFlags b) {
	// backwardSize is intentionally not compared.
	return a.checkType == b.checkType;
    }

    private static StreamFlags decodeStreamFlags(final byte[] buf, final int off) throws UnsupportedOptionsException {
	if (buf[off] != 0x00 || (buf[off + 1] & 0xFF) >= 0x10) {
	    throw new UnsupportedOptionsException();
	}

	final StreamFlags streamFlags = new StreamFlags();
	streamFlags.checkType = buf[off + 1];

	return streamFlags;
    }

    public static StreamFlags decodeStreamFooter(final byte[] buf) throws IOException {
	if (buf[10] != XZ.FOOTER_MAGIC[0] || buf[11] != XZ.FOOTER_MAGIC[1]) {
	    // NOTE: The exception could be XZFormatException too.
	    // It depends on the situation which one is better.
	    throw new CorruptedInputException("XZ Stream Footer is corrupt");
	}

	if (!DecoderUtil.isCRC32Valid(buf, 4, 6, 0)) {
	    throw new CorruptedInputException("XZ Stream Footer is corrupt");
	}

	StreamFlags streamFlags;
	try {
	    streamFlags = DecoderUtil.decodeStreamFlags(buf, 8);
	} catch (final UnsupportedOptionsException e) {
	    throw new UnsupportedOptionsException("Unsupported options in XZ Stream Footer");
	}

	streamFlags.backwardSize = 0;
	for (int i = 0; i < 4; ++i) {
	    streamFlags.backwardSize |= (buf[i + 4] & 0xFF) << i * 8;
	}

	streamFlags.backwardSize = (streamFlags.backwardSize + 1) * 4;

	return streamFlags;
    }

    public static StreamFlags decodeStreamHeader(final byte[] buf) throws IOException {
	for (int i = 0; i < XZ.HEADER_MAGIC.length; ++i) {
	    if (buf[i] != XZ.HEADER_MAGIC[i]) {
		throw new XZFormatException();
	    }
	}

	if (!DecoderUtil.isCRC32Valid(buf, XZ.HEADER_MAGIC.length, 2, XZ.HEADER_MAGIC.length + 2)) {
	    throw new CorruptedInputException("XZ Stream Header is corrupt");
	}

	try {
	    return DecoderUtil.decodeStreamFlags(buf, XZ.HEADER_MAGIC.length);
	} catch (final UnsupportedOptionsException e) {
	    throw new UnsupportedOptionsException("Unsupported options in XZ Stream Header");
	}
    }

    public static long decodeVLI(final InputStream in) throws IOException {
	int b = in.read();
	if (b == -1) {
	    throw new EOFException();
	}

	long num = b & 0x7F;
	int i = 0;

	while ((b & 0x80) != 0x00) {
	    if (++i >= Util.VLI_SIZE_MAX) {
		throw new CorruptedInputException();
	    }

	    b = in.read();
	    if (b == -1) {
		throw new EOFException();
	    }

	    if (b == 0x00) {
		throw new CorruptedInputException();
	    }

	    num |= (long) (b & 0x7F) << i * 7;
	}

	return num;
    }

    public static boolean isCRC32Valid(final byte[] buf, final int off, final int len, final int ref_off) {
	final CRC32 crc32 = new CRC32();
	crc32.update(buf, off, len);
	final long value = crc32.getValue();

	for (int i = 0; i < 4; ++i) {
	    if ((byte) (value >>> i * 8) != buf[ref_off + i]) {
		return false;
	    }
	}

	return true;
    }
}
