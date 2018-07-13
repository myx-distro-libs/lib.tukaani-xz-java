/*
 * LZMA2OutputStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.DataOutputStream;
import java.io.IOException;

import org.tukaani.xz.lz.LZEncoder;
import org.tukaani.xz.lzma.LZMAEncoder;
import org.tukaani.xz.rangecoder.RangeEncoderToBuffer;

class LZMA2OutputStream extends FinishableOutputStream {
    static final int COMPRESSED_SIZE_MAX = 64 << 10;

    private static int getExtraSizeBefore(final int dictSize) {
	return LZMA2OutputStream.COMPRESSED_SIZE_MAX > dictSize ? LZMA2OutputStream.COMPRESSED_SIZE_MAX - dictSize : 0;
    }

    static int getMemoryUsage(final LZMA2Options options) {
	// 64 KiB buffer for the range encoder + a little extra + LZMAEncoder
	final int dictSize = options.getDictSize();
	final int extraSizeBefore = LZMA2OutputStream.getExtraSizeBefore(dictSize);
	return 70 + LZMAEncoder.getMemoryUsage(options.getMode(), dictSize, extraSizeBefore, options.getMatchFinder());
    }

    private FinishableOutputStream out;
    private final DataOutputStream outData;
    private final LZEncoder lz;

    private final RangeEncoderToBuffer rc;
    private final LZMAEncoder lzma;
    private final int props; // Cannot change props on the fly for now.
    private boolean dictResetNeeded = true;

    private boolean stateResetNeeded = true;
    private boolean propsNeeded = true;
    private int pendingSize = 0;

    private boolean finished = false;

    private IOException exception = null;

    private final byte[] tempBuf = new byte[1];

    LZMA2OutputStream(final FinishableOutputStream out, final LZMA2Options options) {
	if (out == null) {
	    throw new NullPointerException();
	}

	this.out = out;
	this.outData = new DataOutputStream(out);
	this.rc = new RangeEncoderToBuffer(LZMA2OutputStream.COMPRESSED_SIZE_MAX);

	final int dictSize = options.getDictSize();
	final int extraSizeBefore = LZMA2OutputStream.getExtraSizeBefore(dictSize);
	this.lzma = LZMAEncoder.getInstance(this.rc, options.getLc(), options.getLp(), options.getPb(),
		options.getMode(), dictSize, extraSizeBefore, options.getNiceLen(), options.getMatchFinder(),
		options.getDepthLimit());

	this.lz = this.lzma.getLZEncoder();

	final byte[] presetDict = options.getPresetDict();
	if (presetDict != null && presetDict.length > 0) {
	    this.lz.setPresetDict(dictSize, presetDict);
	    this.dictResetNeeded = false;
	}

	this.props = (options.getPb() * 5 + options.getLp()) * 9 + options.getLc();
    }

    @Override
    public void close() throws IOException {
	if (this.out != null) {
	    if (!this.finished) {
		try {
		    this.writeEndMarker();
		} catch (final IOException e) {
		}
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

    @Override
    public void finish() throws IOException {
	if (!this.finished) {
	    this.writeEndMarker();

	    try {
		this.out.finish();
	    } catch (final IOException e) {
		this.exception = e;
		throw e;
	    }

	    this.finished = true;
	}
    }

    @Override
    public void flush() throws IOException {
	if (this.exception != null) {
	    throw this.exception;
	}

	if (this.finished) {
	    throw new XZIOException("Stream finished or closed");
	}

	try {
	    this.lz.setFlushing();

	    while (this.pendingSize > 0) {
		this.lzma.encodeForLZMA2();
		this.writeChunk();
	    }

	    this.out.flush();
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}
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

	try {
	    while (len > 0) {
		final int used = this.lz.fillWindow(buf, off, len);
		off += used;
		len -= used;
		this.pendingSize += used;

		if (this.lzma.encodeForLZMA2()) {
		    this.writeChunk();
		}
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

    private void writeChunk() throws IOException {
	final int compressedSize = this.rc.finish();
	int uncompressedSize = this.lzma.getUncompressedSize();

	assert compressedSize > 0 : compressedSize;
	assert uncompressedSize > 0 : uncompressedSize;

	// +2 because the header of a compressed chunk is 2 bytes
	// bigger than the header of an uncompressed chunk.
	if (compressedSize + 2 < uncompressedSize) {
	    this.writeLZMA(uncompressedSize, compressedSize);
	} else {
	    this.lzma.reset();
	    uncompressedSize = this.lzma.getUncompressedSize();
	    assert uncompressedSize > 0 : uncompressedSize;
	    this.writeUncompressed(uncompressedSize);
	}

	this.pendingSize -= uncompressedSize;
	this.lzma.resetUncompressedSize();
	this.rc.reset();
    }

    private void writeEndMarker() throws IOException {
	assert !this.finished;

	if (this.exception != null) {
	    throw this.exception;
	}

	this.lz.setFinishing();

	try {
	    while (this.pendingSize > 0) {
		this.lzma.encodeForLZMA2();
		this.writeChunk();
	    }

	    this.out.write(0x00);
	} catch (final IOException e) {
	    this.exception = e;
	    throw e;
	}

	this.finished = true;
    }

    private void writeLZMA(final int uncompressedSize, final int compressedSize) throws IOException {
	int control;

	if (this.propsNeeded) {
	    if (this.dictResetNeeded) {
		control = 0x80 + (3 << 5);
	    } else {
		control = 0x80 + (2 << 5);
	    }
	} else {
	    if (this.stateResetNeeded) {
		control = 0x80 + (1 << 5);
	    } else {
		control = 0x80;
	    }
	}

	control |= uncompressedSize - 1 >>> 16;
	this.outData.writeByte(control);

	this.outData.writeShort(uncompressedSize - 1);
	this.outData.writeShort(compressedSize - 1);

	if (this.propsNeeded) {
	    this.outData.writeByte(this.props);
	}

	this.rc.write(this.out);

	this.propsNeeded = false;
	this.stateResetNeeded = false;
	this.dictResetNeeded = false;
    }

    private void writeUncompressed(int uncompressedSize) throws IOException {
	while (uncompressedSize > 0) {
	    final int chunkSize = Math.min(uncompressedSize, LZMA2OutputStream.COMPRESSED_SIZE_MAX);
	    this.outData.writeByte(this.dictResetNeeded ? 0x01 : 0x02);
	    this.outData.writeShort(chunkSize - 1);
	    this.lz.copyUncompressed(this.out, uncompressedSize, chunkSize);
	    uncompressedSize -= chunkSize;
	    this.dictResetNeeded = false;
	}

	this.stateResetNeeded = true;
    }
}
