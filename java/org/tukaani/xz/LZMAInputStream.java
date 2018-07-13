/*
 * LZMAInputStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.tukaani.xz.lz.LZDecoder;
import org.tukaani.xz.lzma.LZMADecoder;
import org.tukaani.xz.rangecoder.RangeDecoderFromStream;

/**
 * Decompresses legacy .lzma files and raw LZMA streams (no .lzma header).
 * <p>
 * <b>IMPORTANT:</b> In contrast to other classes in this package, this class
 * reads data from its input stream one byte at a time. If the input stream is
 * for example {@link java.io.FileInputStream}, wrapping it into
 * {@link java.io.BufferedInputStream} tends to improve performance a lot. This
 * is not automatically done by this class because there may be use cases where
 * it is desired that this class won't read any bytes past the end of the LZMA
 * stream.
 * <p>
 * Even when using <code>BufferedInputStream</code>, the performance tends to be
 * worse (maybe 10-20&nbsp;% slower) than with {@link LZMA2InputStream} or
 * {@link XZInputStream} (when the .xz file contains LZMA2-compressed data).
 *
 * @since 1.4
 */
public class LZMAInputStream extends InputStream {
    /**
     * Largest dictionary size supported by this implementation.
     * <p>
     * LZMA allows dictionaries up to one byte less than 4 GiB. This
     * implementation supports only 16 bytes less than 2 GiB. This limitation is
     * due to Java using signed 32-bit integers for array indexing. The
     * limitation shouldn't matter much in practice since so huge dictionaries
     * are not normally used.
     */
    public static final int DICT_SIZE_MAX = Integer.MAX_VALUE & ~15;

    private static int getDictSize(int dictSize) {
	if (dictSize < 0 || dictSize > LZMAInputStream.DICT_SIZE_MAX) {
	    throw new IllegalArgumentException("LZMA dictionary is too big for this implementation");
	}

	// For performance reasons, use a 4 KiB dictionary if something
	// smaller was requested. It's a rare situation and the performance
	// difference isn't huge, and it starts to matter mostly when the
	// dictionary is just a few bytes. But we need to handle the special
	// case of dictSize == 0 anyway, which is an allowed value but in
	// practice means one-byte dictionary.
	//
	// Note that using a dictionary bigger than specified in the headers
	// can hide errors if there is a reference to data beyond the original
	// dictionary size but is still within 4 KiB.
	if (dictSize < 4096) {
	    dictSize = 4096;
	}

	// Round dictionary size upward to a multiple of 16. This way LZMA
	// can use LZDecoder.getPos() for calculating LZMA's posMask.
	return dictSize + 15 & ~15;
    }

    /**
     * Gets approximate decompressor memory requirements as kibibytes for the
     * given dictionary size and LZMA properties byte (lc, lp, and pb).
     *
     * @param dictSize
     *            LZMA dictionary size as bytes, should be in the range [
     *            <code>0</code>, <code>DICT_SIZE_MAX</code>]
     *
     * @param propsByte
     *            LZMA properties byte that encodes the values of lc, lp, and pb
     *
     * @return approximate memory requirements as kibibytes (KiB)
     *
     * @throws UnsupportedOptionsException
     *             if <code>dictSize</code> is outside the range [<code>0</code>
     *             , <code>DICT_SIZE_MAX</code>]
     *
     * @throws CorruptedInputException
     *             if <code>propsByte</code> is invalid
     */
    public static int getMemoryUsage(final int dictSize, final byte propsByte)
	    throws UnsupportedOptionsException, CorruptedInputException {
	if (dictSize < 0 || dictSize > LZMAInputStream.DICT_SIZE_MAX) {
	    throw new UnsupportedOptionsException("LZMA dictionary is too big for this implementation");
	}

	int props = propsByte & 0xFF;
	if (props > (4 * 5 + 4) * 9 + 8) {
	    throw new CorruptedInputException("Invalid LZMA properties byte");
	}

	props %= 9 * 5;
	final int lp = props / 9;
	final int lc = props - lp * 9;

	return LZMAInputStream.getMemoryUsage(dictSize, lc, lp);
    }

    /**
     * Gets approximate decompressor memory requirements as kibibytes for the
     * given dictionary size, lc, and lp. Note that pb isn't needed.
     *
     * @param dictSize
     *            LZMA dictionary size as bytes, must be in the range [
     *            <code>0</code>, <code>DICT_SIZE_MAX</code>]
     *
     * @param lc
     *            number of literal context bits, must be in the range [0, 8]
     *
     * @param lp
     *            number of literal position bits, must be in the range [0, 4]
     *
     * @return approximate memory requirements as kibibytes (KiB)
     */
    public static int getMemoryUsage(final int dictSize, final int lc, final int lp) {
	if (lc < 0 || lc > 8 || lp < 0 || lp > 4) {
	    throw new IllegalArgumentException("Invalid lc or lp");
	}

	// Probability variables have the type "short". There are
	// 0x300 (768) probability variables in each literal subcoder.
	// The number of literal subcoders is 2^(lc + lp).
	//
	// Roughly 10 KiB for the base state + LZ decoder's dictionary buffer
	// + sizeof(short) * number probability variables per literal subcoder
	// * number of literal subcoders
	return 10 + LZMAInputStream.getDictSize(dictSize) / 1024 + (2 * 0x300 << lc + lp) / 1024;
    }

    private InputStream in;

    private LZDecoder lz;

    private RangeDecoderFromStream rc;

    private LZMADecoder lzma;

    private boolean endReached = false;

    private final byte[] tempBuf = new byte[1];

    /**
     * Number of uncompressed bytes left to be decompressed, or -1 if the end
     * marker is used.
     */
    private long remainingSize;

    private IOException exception = null;

    /**
     * Creates a new .lzma file format decompressor without a memory usage
     * limit.
     *
     * @param in
     *            input stream from which .lzma data is read; it might be a good
     *            idea to wrap it in <code>BufferedInputStream</code>, see the
     *            note at the top of this page
     *
     * @throws CorruptedInputException
     *             file is corrupt or perhaps not in the .lzma format at all
     *
     * @throws UnsupportedOptionsException
     *             dictionary size or uncompressed size is too big for this
     *             implementation
     *
     * @throws EOFException
     *             file is truncated or perhaps not in the .lzma format at all
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     */
    public LZMAInputStream(final InputStream in) throws IOException {
	this(in, -1);
    }

    /**
     * Creates a new .lzma file format decompressor with an optional memory
     * usage limit.
     *
     * @param in
     *            input stream from which .lzma data is read; it might be a good
     *            idea to wrap it in <code>BufferedInputStream</code>, see the
     *            note at the top of this page
     *
     * @param memoryLimit
     *            memory usage limit in kibibytes (KiB) or <code>-1</code> to
     *            impose no memory usage limit
     *
     * @throws CorruptedInputException
     *             file is corrupt or perhaps not in the .lzma format at all
     *
     * @throws UnsupportedOptionsException
     *             dictionary size or uncompressed size is too big for this
     *             implementation
     *
     * @throws MemoryLimitException
     *             memory usage limit was exceeded
     *
     * @throws EOFException
     *             file is truncated or perhaps not in the .lzma format at all
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     */
    public LZMAInputStream(final InputStream in, final int memoryLimit) throws IOException {
	final DataInputStream inData = new DataInputStream(in);

	// Properties byte (lc, lp, and pb)
	final byte propsByte = inData.readByte();

	// Dictionary size is an unsigned 32-bit little endian integer.
	int dictSize = 0;
	for (int i = 0; i < 4; ++i) {
	    dictSize |= inData.readUnsignedByte() << 8 * i;
	}

	// Uncompressed size is an unsigned 64-bit little endian integer.
	// The maximum 64-bit value is a special case (becomes -1 here)
	// which indicates that the end marker is used instead of knowing
	// the uncompressed size beforehand.
	long uncompSize = 0;
	for (int i = 0; i < 8; ++i) {
	    uncompSize |= (long) inData.readUnsignedByte() << 8 * i;
	}

	// Check the memory usage limit.
	final int memoryNeeded = LZMAInputStream.getMemoryUsage(dictSize, propsByte);
	if (memoryLimit != -1 && memoryNeeded > memoryLimit) {
	    throw new MemoryLimitException(memoryNeeded, memoryLimit);
	}

	this.initialize(in, uncompSize, propsByte, dictSize, null);
    }

    /**
     * Creates a new input stream that decompresses raw LZMA data (no .lzma
     * header) from <code>in</code>.
     * <p>
     * The caller needs to know if the "end of payload marker (EOPM)" alias
     * "end of stream marker (EOS marker)" alias "end marker" present. If the
     * end marker isn't used, the caller must know the exact uncompressed size
     * of the stream.
     * <p>
     * The caller also needs to provide the LZMA properties byte that encodes
     * the number of literal context bits (lc), literal position bits (lp), and
     * position bits (pb).
     * <p>
     * The dictionary size used when compressing is also needed. Specifying a
     * too small dictionary size will prevent decompressing the stream.
     * Specifying a too big dictionary is waste of memory but decompression will
     * work.
     * <p>
     * There is no need to specify a dictionary bigger than the uncompressed
     * size of the data even if a bigger dictionary was used when compressing.
     * If you know the uncompressed size of the data, this might allow saving
     * some memory.
     *
     * @param in
     *            input stream from which compressed data is read
     *
     * @param uncompSize
     *            uncompressed size of the LZMA stream or -1 if the end marker
     *            is used in the LZMA stream
     *
     * @param propsByte
     *            LZMA properties byte that has the encoded values for literal
     *            context bits (lc), literal position bits (lp), and position
     *            bits (pb)
     *
     * @param dictSize
     *            dictionary size as bytes, must be in the range [<code>0</code>
     *            , <code>DICT_SIZE_MAX</code>]
     *
     * @throws CorruptedInputException
     *             if <code>propsByte</code> is invalid or the first input byte
     *             is not 0x00
     *
     * @throws UnsupportedOptionsException
     *             dictionary size or uncompressed size is too big for this
     *             implementation
     *
     *
     */
    public LZMAInputStream(final InputStream in, final long uncompSize, final byte propsByte, final int dictSize)
	    throws IOException {
	this.initialize(in, uncompSize, propsByte, dictSize, null);
    }

    /**
     * Creates a new input stream that decompresses raw LZMA data (no .lzma
     * header) from <code>in</code> optionally with a preset dictionary.
     *
     * @param in
     *            input stream from which LZMA-compressed data is read
     *
     * @param uncompSize
     *            uncompressed size of the LZMA stream or -1 if the end marker
     *            is used in the LZMA stream
     *
     * @param propsByte
     *            LZMA properties byte that has the encoded values for literal
     *            context bits (lc), literal position bits (lp), and position
     *            bits (pb)
     *
     * @param dictSize
     *            dictionary size as bytes, must be in the range [<code>0</code>
     *            , <code>DICT_SIZE_MAX</code>]
     *
     * @param presetDict
     *            preset dictionary or <code>null</code> to use no preset
     *            dictionary
     *
     * @throws CorruptedInputException
     *             if <code>propsByte</code> is invalid or the first input byte
     *             is not 0x00
     *
     * @throws UnsupportedOptionsException
     *             dictionary size or uncompressed size is too big for this
     *             implementation
     *
     * @throws EOFException
     *             file is truncated or corrupt
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     */
    public LZMAInputStream(final InputStream in, final long uncompSize, final byte propsByte, final int dictSize,
	    final byte[] presetDict) throws IOException {
	this.initialize(in, uncompSize, propsByte, dictSize, presetDict);
    }

    /**
     * Creates a new input stream that decompresses raw LZMA data (no .lzma
     * header) from <code>in</code> optionally with a preset dictionary.
     *
     * @param in
     *            input stream from which LZMA-compressed data is read
     *
     * @param uncompSize
     *            uncompressed size of the LZMA stream or -1 if the end marker
     *            is used in the LZMA stream
     *
     * @param lc
     *            number of literal context bits, must be in the range [0, 8]
     *
     * @param lp
     *            number of literal position bits, must be in the range [0, 4]
     *
     * @param pb
     *            number position bits, must be in the range [0, 4]
     *
     * @param dictSize
     *            dictionary size as bytes, must be in the range [<code>0</code>
     *            , <code>DICT_SIZE_MAX</code>]
     *
     * @param presetDict
     *            preset dictionary or <code>null</code> to use no preset
     *            dictionary
     *
     * @throws CorruptedInputException
     *             if the first input byte is not 0x00
     *
     * @throws EOFException
     *             file is truncated or corrupt
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     */
    public LZMAInputStream(final InputStream in, final long uncompSize, final int lc, final int lp, final int pb,
	    final int dictSize, final byte[] presetDict) throws IOException {
	this.initialize(in, uncompSize, lc, lp, pb, dictSize, presetDict);
    }

    /**
     * Closes the stream and calls <code>in.close()</code>. If the stream was
     * already closed, this does nothing.
     *
     * @throws IOException
     *             if thrown by <code>in.close()</code>
     */
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

    private void initialize(final InputStream in, final long uncompSize, final byte propsByte, final int dictSize,
	    final byte[] presetDict) throws IOException {
	// Validate the uncompressed size since the other "initialize" throws
	// IllegalArgumentException if uncompSize < -1.
	if (uncompSize < -1) {
	    throw new UnsupportedOptionsException("Uncompressed size is too big");
	}

	// Decode the properties byte. In contrast to LZMA2, there is no
	// limit of lc + lp <= 4.
	int props = propsByte & 0xFF;
	if (props > (4 * 5 + 4) * 9 + 8) {
	    throw new CorruptedInputException("Invalid LZMA properties byte");
	}

	final int pb = props / (9 * 5);
	props -= pb * 9 * 5;
	final int lp = props / 9;
	final int lc = props - lp * 9;

	// Validate the dictionary size since the other "initialize" throws
	// IllegalArgumentException if dictSize is not supported.
	if (dictSize < 0 || dictSize > LZMAInputStream.DICT_SIZE_MAX) {
	    throw new UnsupportedOptionsException("LZMA dictionary is too big for this implementation");
	}

	this.initialize(in, uncompSize, lc, lp, pb, dictSize, presetDict);
    }

    private void initialize(final InputStream in, final long uncompSize, final int lc, final int lp, final int pb,
	    int dictSize, final byte[] presetDict) throws IOException {
	// getDictSize validates dictSize and gives a message in
	// the exception too, so skip validating dictSize here.
	if (uncompSize < -1 || lc < 0 || lc > 8 || lp < 0 || lp > 4 || pb < 0 || pb > 4) {
	    throw new IllegalArgumentException();
	}

	this.in = in;

	// If uncompressed size is known, use it to avoid wasting memory for
	// a uselessly large dictionary buffer.
	dictSize = LZMAInputStream.getDictSize(dictSize);
	if (uncompSize >= 0 && dictSize > uncompSize) {
	    dictSize = LZMAInputStream.getDictSize((int) uncompSize);
	}

	this.lz = new LZDecoder(LZMAInputStream.getDictSize(dictSize), presetDict);
	this.rc = new RangeDecoderFromStream(in);
	this.lzma = new LZMADecoder(this.lz, this.rc, lc, lp, pb);
	this.remainingSize = uncompSize;
    }

    /**
     * Decompresses the next byte from this input stream.
     * <p>
     * Reading lots of data with <code>read()</code> from this input stream may
     * be inefficient. Wrap it in <code>java.io.BufferedInputStream</code> if
     * you need to read lots of data one byte at a time.
     *
     * @return the next decompressed byte, or <code>-1</code> to indicate the
     *         end of the compressed stream
     *
     * @throws CorruptedInputException
     *
     * @throws XZIOException
     *             if the stream has been closed
     *
     * @throws EOFException
     *             compressed input is truncated or corrupt
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     */
    @Override
    public int read() throws IOException {
	return this.read(this.tempBuf, 0, 1) == -1 ? -1 : this.tempBuf[0] & 0xFF;
    }

    /**
     * Decompresses into an array of bytes.
     * <p>
     * If <code>len</code> is zero, no bytes are read and <code>0</code> is
     * returned. Otherwise this will block until <code>len</code> bytes have
     * been decompressed, the end of the LZMA stream is reached, or an exception
     * is thrown.
     *
     * @param buf
     *            target buffer for uncompressed data
     * @param off
     *            start offset in <code>buf</code>
     * @param len
     *            maximum number of uncompressed bytes to read
     *
     * @return number of bytes read, or <code>-1</code> to indicate the end of
     *         the compressed stream
     *
     * @throws CorruptedInputException
     *
     * @throws XZIOException
     *             if the stream has been closed
     *
     * @throws EOFException
     *             compressed input is truncated or corrupt
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     */
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

	if (this.endReached) {
	    return -1;
	}

	try {
	    int size = 0;

	    while (len > 0) {
		// If uncompressed size is known and thus no end marker will
		// be present, set the limit so that the uncompressed size
		// won't be exceeded.
		int copySizeMax = len;
		if (this.remainingSize >= 0 && this.remainingSize < len) {
		    copySizeMax = (int) this.remainingSize;
		}

		this.lz.setLimit(copySizeMax);

		// Decode into the dictionary buffer.
		try {
		    this.lzma.decode();
		} catch (final CorruptedInputException e) {
		    // The end marker is encoded with a LZMA symbol that
		    // indicates maximum match distance. This is larger
		    // than any supported dictionary and thus causes
		    // CorruptedInputException from LZDecoder.repeat.
		    if (this.remainingSize != -1 || !this.lzma.endMarkerDetected()) {
			throw e;
		    }

		    this.endReached = true;

		    // The exception makes lzma.decode() miss the last range
		    // decoder normalization, so do it here. This might
		    // cause an IOException if it needs to read a byte
		    // from the input stream.
		    this.rc.normalize();
		}

		// Copy from the dictionary to buf.
		final int copiedSize = this.lz.flush(buf, off);
		off += copiedSize;
		len -= copiedSize;
		size += copiedSize;

		if (this.remainingSize >= 0) {
		    // Update the number of bytes left to be decompressed.
		    this.remainingSize -= copiedSize;
		    assert this.remainingSize >= 0;

		    if (this.remainingSize == 0) {
			this.endReached = true;
		    }
		}

		if (this.endReached) {
		    // Checking these helps a lot when catching corrupt
		    // or truncated .lzma files. LZMA Utils doesn't do
		    // the first check and thus it accepts many invalid
		    // files that this implementation and XZ Utils don't.
		    if (!this.rc.isFinished() || this.lz.hasPending()) {
			throw new CorruptedInputException();
		    }

		    return size == 0 ? -1 : size;
		}
	    }

	    return size;

	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}
    }
}
