/*
 * SingleXZInputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.tukaani.xz.check.Check;
import org.tukaani.xz.common.DecoderUtil;
import org.tukaani.xz.common.StreamFlags;
import org.tukaani.xz.common.Util;
import org.tukaani.xz.index.IndexHash;

/**
 * Decompresses exactly one XZ Stream in streamed mode (no seeking). The
 * decompression stops after the first XZ Stream has been decompressed, and the
 * read position in the input stream is left at the first byte after the end of
 * the XZ Stream. This can be useful when XZ data has been stored inside some
 * other file format or protocol.
 * <p>
 * Unless you know what you are doing, don't use this class to decompress
 * standalone .xz files. For that purpose, use <code>XZInputStream</code>.
 *
 * <h4>When uncompressed size is known beforehand</h4>
 * <p>
 * If you are decompressing complete XZ streams and your application knows
 * exactly how much uncompressed data there should be, it is good to try reading
 * one more byte by calling <code>read()</code> and checking that it returns
 * <code>-1</code>. This way the decompressor will parse the file footers and
 * verify the integrity checks, giving the caller more confidence that the
 * uncompressed data is valid.
 *
 * @see XZInputStream
 */
public class SingleXZInputStream extends InputStream {
    /**
     * Reads the Stream Header into a buffer. This is a helper function for the
     * constructors.
     */
    private static byte[] readStreamHeader(final InputStream in) throws IOException {
	final byte[] streamHeader = new byte[Util.STREAM_HEADER_SIZE];
	new DataInputStream(in).readFully(streamHeader);
	return streamHeader;
    }

    private InputStream in;
    private final int memoryLimit;
    private final StreamFlags streamHeaderFlags;
    private final Check check;
    private final boolean verifyCheck;
    private BlockInputStream blockDecoder = null;
    private final IndexHash indexHash = new IndexHash();
    private boolean endReached = false;

    private IOException exception = null;

    private final byte[] tempBuf = new byte[1];

    /**
     * Creates a new XZ decompressor that decompresses exactly one XZ Stream
     * from <code>in</code> without a memory usage limit.
     * <p>
     * This constructor reads and parses the XZ Stream Header (12 bytes) from
     * <code>in</code>. The header of the first Block is not read until
     * <code>read</code> is called.
     *
     * @param in
     *            input stream from which XZ-compressed data is read
     *
     * @throws XZFormatException
     *             input is not in the XZ format
     *
     * @throws CorruptedInputException
     *             XZ header CRC32 doesn't match
     *
     * @throws UnsupportedOptionsException
     *             XZ header is valid but specifies options not supported by
     *             this implementation
     *
     * @throws EOFException
     *             less than 12 bytes of input was available from
     *             <code>in</code>
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     */
    public SingleXZInputStream(final InputStream in) throws IOException {
	this(in, -1);
    }

    /**
     * Creates a new XZ decompressor that decompresses exactly one XZ Stream
     * from <code>in</code> with an optional memory usage limit.
     * <p>
     * This is identical to <code>SingleXZInputStream(InputStream)</code> except
     * that this takes also the <code>memoryLimit</code> argument.
     *
     * @param in
     *            input stream from which XZ-compressed data is read
     *
     * @param memoryLimit
     *            memory usage limit in kibibytes (KiB) or <code>-1</code> to
     *            impose no memory usage limit
     *
     * @throws XZFormatException
     *             input is not in the XZ format
     *
     * @throws CorruptedInputException
     *             XZ header CRC32 doesn't match
     *
     * @throws UnsupportedOptionsException
     *             XZ header is valid but specifies options not supported by
     *             this implementation
     *
     * @throws EOFException
     *             less than 12 bytes of input was available from
     *             <code>in</code>
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     */
    public SingleXZInputStream(final InputStream in, final int memoryLimit) throws IOException {
	this(in, memoryLimit, true, SingleXZInputStream.readStreamHeader(in));
    }

    /**
     * Creates a new XZ decompressor that decompresses exactly one XZ Stream
     * from <code>in</code> with an optional memory usage limit and ability to
     * disable verification of integrity checks.
     * <p>
     * This is identical to <code>SingleXZInputStream(InputStream,int)</code>
     * except that this takes also the <code>verifyCheck</code> argument.
     * <p>
     * Note that integrity check verification should almost never be disabled.
     * Possible reasons to disable integrity check verification:
     * <ul>
     * <li>Trying to recover data from a corrupt .xz file.</li>
     * <li>Speeding up decompression. This matters mostly with SHA-256 or with
     * files that have compressed extremely well. It's recommended that
     * integrity checking isn't disabled for performance reasons unless the file
     * integrity is verified externally in some other way.</li>
     * </ul>
     * <p>
     * <code>verifyCheck</code> only affects the integrity check of the actual
     * compressed data. The CRC32 fields in the headers are always verified.
     *
     * @param in
     *            input stream from which XZ-compressed data is read
     *
     * @param memoryLimit
     *            memory usage limit in kibibytes (KiB) or <code>-1</code> to
     *            impose no memory usage limit
     *
     * @param verifyCheck
     *            if <code>true</code>, the integrity checks will be verified;
     *            this should almost never be set to <code>false</code>
     *
     * @throws XZFormatException
     *             input is not in the XZ format
     *
     * @throws CorruptedInputException
     *             XZ header CRC32 doesn't match
     *
     * @throws UnsupportedOptionsException
     *             XZ header is valid but specifies options not supported by
     *             this implementation
     *
     * @throws EOFException
     *             less than 12 bytes of input was available from
     *             <code>in</code>
     *
     * @throws IOException
     *             may be thrown by <code>in</code>
     *
     * @since 1.6
     */
    public SingleXZInputStream(final InputStream in, final int memoryLimit, final boolean verifyCheck)
	    throws IOException {
	this(in, memoryLimit, verifyCheck, SingleXZInputStream.readStreamHeader(in));
    }

    SingleXZInputStream(final InputStream in, final int memoryLimit, final boolean verifyCheck,
	    final byte[] streamHeader) throws IOException {
	this.in = in;
	this.memoryLimit = memoryLimit;
	this.verifyCheck = verifyCheck;
	this.streamHeaderFlags = DecoderUtil.decodeStreamHeader(streamHeader);
	this.check = Check.getInstance(this.streamHeaderFlags.checkType);
    }

    /**
     * Returns the number of uncompressed bytes that can be read without
     * blocking. The value is returned with an assumption that the compressed
     * input data will be valid. If the compressed data is corrupt,
     * <code>CorruptedInputException</code> may get thrown before the number of
     * bytes claimed to be available have been read from this input stream.
     *
     * @return the number of uncompressed bytes that can be read without
     *         blocking
     */
    @Override
    public int available() throws IOException {
	if (this.in == null) {
	    throw new XZIOException("Stream closed");
	}

	if (this.exception != null) {
	    throw this.exception;
	}

	return this.blockDecoder == null ? 0 : this.blockDecoder.available();
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

    /**
     * Gets the name of the integrity check used in this XZ Stream.
     *
     * @return the name of the check specified in the XZ Stream Header
     */
    public String getCheckName() {
	return this.check.getName();
    }

    /**
     * Gets the ID of the integrity check used in this XZ Stream.
     *
     * @return the Check ID specified in the XZ Stream Header
     */
    public int getCheckType() {
	return this.streamHeaderFlags.checkType;
    }

    /**
     * Decompresses the next byte from this input stream.
     * <p>
     * Reading lots of data with <code>read()</code> from this input stream may
     * be inefficient. Wrap it in {@link java.io.BufferedInputStream} if you
     * need to read lots of data one byte at a time.
     *
     * @return the next decompressed byte, or <code>-1</code> to indicate the
     *         end of the compressed stream
     *
     * @throws CorruptedInputException
     * @throws UnsupportedOptionsException
     * @throws MemoryLimitException
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
     * returned. Otherwise this will try to decompress <code>len</code> bytes of
     * uncompressed data. Less than <code>len</code> bytes may be read only in
     * the following situations:
     * <ul>
     * <li>The end of the compressed data was reached successfully.</li>
     * <li>An error is detected after at least one but less <code>len</code>
     * bytes have already been successfully decompressed. The next call with
     * non-zero <code>len</code> will immediately throw the pending exception.
     * </li>
     * <li>An exception is thrown.</li>
     * </ul>
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
     * @throws UnsupportedOptionsException
     * @throws MemoryLimitException
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

	int size = 0;

	try {
	    while (len > 0) {
		if (this.blockDecoder == null) {
		    try {
			this.blockDecoder = new BlockInputStream(this.in, this.check, this.verifyCheck,
				this.memoryLimit, -1, -1);
		    } catch (final IndexIndicatorException e) {
			this.indexHash.validate(this.in);
			this.validateStreamFooter();
			this.endReached = true;
			return size > 0 ? size : -1;
		    }
		}

		final int ret = this.blockDecoder.read(buf, off, len);

		if (ret > 0) {
		    size += ret;
		    off += ret;
		    len -= ret;
		} else if (ret == -1) {
		    this.indexHash.add(this.blockDecoder.getUnpaddedSize(), this.blockDecoder.getUncompressedSize());
		    this.blockDecoder = null;
		}
	    }
	} catch (final IOException e) {
	    this.exception = e;
	    if (size == 0) {
		throw e;
	    }
	}

	return size;
    }

    private void validateStreamFooter() throws IOException {
	final byte[] buf = new byte[Util.STREAM_HEADER_SIZE];
	new DataInputStream(this.in).readFully(buf);
	final StreamFlags streamFooterFlags = DecoderUtil.decodeStreamFooter(buf);

	if (!DecoderUtil.areStreamFlagsEqual(this.streamHeaderFlags, streamFooterFlags)
		|| this.indexHash.getIndexSize() != streamFooterFlags.backwardSize) {
	    throw new CorruptedInputException("XZ Stream Footer does not match Stream Header");
	}
    }
}
