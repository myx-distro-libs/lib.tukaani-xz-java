/*
 * LZMAOutputStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.IOException;
import java.io.OutputStream;

import org.tukaani.xz.lz.LZEncoder;
import org.tukaani.xz.lzma.LZMAEncoder;
import org.tukaani.xz.rangecoder.RangeEncoderToStream;

/**
 * Compresses into the legacy .lzma file format or into a raw LZMA stream.
 *
 * @since 1.6
 */
public class LZMAOutputStream extends FinishableOutputStream {
    private OutputStream out;

    private final LZEncoder lz;
    private final RangeEncoderToStream rc;
    private final LZMAEncoder lzma;

    private final int props;
    private final boolean useEndMarker;
    private final long expectedUncompressedSize;
    private long currentUncompressedSize = 0;

    private boolean finished = false;
    private IOException exception = null;

    private final byte[] tempBuf = new byte[1];

    /**
     * Creates a new compressor for raw LZMA (also known as LZMA1) stream.
     * <p>
     * Raw LZMA streams can be encoded with or without end of stream marker.
     * When decompressing the stream, one must know if the end marker was used
     * and tell it to the decompressor. If the end marker wasn't used, the
     * decompressor will also need to know the uncompressed size.
     *
     * @param out
     *            output stream to which the compressed data will be written
     *
     * @param options
     *            LZMA compression options; the same class is used here as is
     *            for LZMA2
     *
     * @param useEndMarker
     *            if end of stream marker should be written
     *
     * @throws IOException
     *             may be thrown from <code>out</code>
     */
    public LZMAOutputStream(final OutputStream out, final LZMA2Options options, final boolean useEndMarker)
	    throws IOException {
	this(out, options, false, useEndMarker, -1);
    }

    private LZMAOutputStream(final OutputStream out, final LZMA2Options options, final boolean useHeader,
	    final boolean useEndMarker, final long expectedUncompressedSize) throws IOException {
	if (out == null) {
	    throw new NullPointerException();
	}

	// -1 indicates unknown and >= 0 are for known sizes.
	if (expectedUncompressedSize < -1) {
	    throw new IllegalArgumentException("Invalid expected input size (less than -1)");
	}

	this.useEndMarker = useEndMarker;
	this.expectedUncompressedSize = expectedUncompressedSize;

	this.out = out;
	this.rc = new RangeEncoderToStream(out);

	int dictSize = options.getDictSize();
	this.lzma = LZMAEncoder.getInstance(this.rc, options.getLc(), options.getLp(), options.getPb(),
		options.getMode(), dictSize, 0, options.getNiceLen(), options.getMatchFinder(),
		options.getDepthLimit());

	this.lz = this.lzma.getLZEncoder();

	final byte[] presetDict = options.getPresetDict();
	if (presetDict != null && presetDict.length > 0) {
	    if (useHeader) {
		throw new UnsupportedOptionsException(
			"Preset dictionary cannot be used in .lzma files " + "(try a raw LZMA stream instead)");
	    }

	    this.lz.setPresetDict(dictSize, presetDict);
	}

	this.props = (options.getPb() * 5 + options.getLp()) * 9 + options.getLc();

	if (useHeader) {
	    // Props byte stores lc, lp, and pb.
	    out.write(this.props);

	    // Dictionary size is stored as a 32-bit unsigned little endian
	    // integer.
	    for (int i = 0; i < 4; ++i) {
		out.write(dictSize & 0xFF);
		dictSize >>>= 8;
	    }

	    // Uncompressed size is stored as a 64-bit unsigned little endian
	    // integer. The max value (-1 in two's complement) indicates
	    // unknown size.
	    for (int i = 0; i < 8; ++i) {
		out.write((int) (expectedUncompressedSize >>> 8 * i) & 0xFF);
	    }
	}
    }

    /**
     * Creates a new compressor for the legacy .lzma file format.
     * <p>
     * If the uncompressed size of the input data is known, it will be stored in
     * the .lzma header and no end of stream marker will be used. Otherwise the
     * header will indicate unknown uncompressed size and the end of stream
     * marker will be used.
     * <p>
     * Note that a preset dictionary cannot be used in .lzma files but it can be
     * used for raw LZMA streams.
     *
     * @param out
     *            output stream to which the compressed data will be written
     *
     * @param options
     *            LZMA compression options; the same class is used here as is
     *            for LZMA2
     *
     * @param inputSize
     *            uncompressed size of the data to be compressed; use
     *            <code>-1</code> when unknown
     *
     * @throws IOException
     *             may be thrown from <code>out</code>
     */
    public LZMAOutputStream(final OutputStream out, final LZMA2Options options, final long inputSize)
	    throws IOException {
	this(out, options, true, inputSize == -1, inputSize);
    }

    /**
     * Finishes the stream and closes the underlying OutputStream.
     */
    @Override
    public void close() throws IOException {
	if (this.out != null) {
	    try {
		this.finish();
	    } catch (final IOException e) {
	    }

	    try {
		this.out.close();
	    } catch (final IOException e) {
		if (this.exception == null) {
		    this.exception = e;
		}
	    }

	    this.out = null;
	}

	if (this.exception != null) {
	    throw this.exception;
	}
    }

    /**
     * Finishes the stream without closing the underlying OutputStream.
     */
    @Override
    public void finish() throws IOException {
	if (!this.finished) {
	    if (this.exception != null) {
		throw this.exception;
	    }

	    try {
		if (this.expectedUncompressedSize != -1
			&& this.expectedUncompressedSize != this.currentUncompressedSize) {
		    throw new XZIOException("Expected uncompressed size (" + this.expectedUncompressedSize
			    + ") doesn't equal " + "the number of bytes written to the stream ("
			    + this.currentUncompressedSize + ")");
		}

		this.lz.setFinishing();
		this.lzma.encodeForLZMA1();

		if (this.useEndMarker) {
		    this.lzma.encodeLZMA1EndMarker();
		}

		this.rc.finish();
	    } catch (final IOException e) {
		this.exception = e;
		throw e;
	    }

	    this.finished = true;
	}
    }

    /**
     * Flushing isn't supported and will throw XZIOException.
     */
    @Override
    public void flush() throws IOException {
	throw new XZIOException("LZMAOutputStream does not support flushing");
    }

    /**
     * Returns the LZMA lc/lp/pb properties encoded into a single byte. This
     * might be useful when handling file formats other than .lzma that use the
     * same encoding for the LZMA properties as .lzma does.
     */
    public int getProps() {
	return this.props;
    }

    /**
     * Gets the amount of uncompressed data written to the stream. This is
     * useful when creating raw LZMA streams without the end of stream marker.
     */
    public long getUncompressedSize() {
	return this.currentUncompressedSize;
    }

    @Override
    public void write(final byte[] buf, int off, int len) throws IOException {
	if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) {
	    throw new IndexOutOfBoundsException();
	}

	if (this.exception != null) {
	    throw this.exception;
	}

	if (this.finished) {
	    throw new XZIOException("Stream finished or closed");
	}

	if (this.expectedUncompressedSize != -1 && this.expectedUncompressedSize - this.currentUncompressedSize < len) {
	    throw new XZIOException(
		    "Expected uncompressed input size (" + this.expectedUncompressedSize + " bytes) was exceeded");
	}

	this.currentUncompressedSize += len;

	try {
	    while (len > 0) {
		final int used = this.lz.fillWindow(buf, off, len);
		off += used;
		len -= used;
		this.lzma.encodeForLZMA1();
	    }
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}
    }

    @Override
    public void write(final int b) throws IOException {
	this.tempBuf[0] = (byte) b;
	this.write(this.tempBuf, 0, 1);
    }
}
