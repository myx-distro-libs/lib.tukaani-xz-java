/*
 * BlockOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.tukaani.xz.check.Check;
import org.tukaani.xz.common.EncoderUtil;
import org.tukaani.xz.common.Util;

class BlockOutputStream extends FinishableOutputStream {
    private final OutputStream out;
    private final CountingOutputStream outCounted;
    private FinishableOutputStream filterChain;
    private final Check check;

    private final int headerSize;
    private final long compressedSizeLimit;
    private long uncompressedSize = 0;

    private final byte[] tempBuf = new byte[1];

    public BlockOutputStream(final OutputStream out, final FilterEncoder[] filters, final Check check)
	    throws IOException {
	this.out = out;
	this.check = check;

	// Initialize the filter chain.
	this.outCounted = new CountingOutputStream(out);
	this.filterChain = this.outCounted;
	for (int i = filters.length - 1; i >= 0; --i) {
	    this.filterChain = filters[i].getOutputStream(this.filterChain);
	}

	// Prepare to encode the Block Header field.
	final ByteArrayOutputStream bufStream = new ByteArrayOutputStream();

	// Write a dummy Block Header Size field. The real value is written
	// once everything else except CRC32 has been written.
	bufStream.write(0x00);

	// Write Block Flags. Storing Compressed Size or Uncompressed Size
	// isn't supported for now.
	bufStream.write(filters.length - 1);

	// List of Filter Flags
	for (final FilterEncoder filter : filters) {
	    EncoderUtil.encodeVLI(bufStream, filter.getFilterID());
	    final byte[] filterProps = filter.getFilterProps();
	    EncoderUtil.encodeVLI(bufStream, filterProps.length);
	    bufStream.write(filterProps);
	}

	// Header Padding
	while ((bufStream.size() & 3) != 0) {
	    bufStream.write(0x00);
	}

	final byte[] buf = bufStream.toByteArray();

	// Total size of the Block Header: Take the size of the CRC32 field
	// into account.
	this.headerSize = buf.length + 4;

	// This is just a sanity check.
	if (this.headerSize > Util.BLOCK_HEADER_SIZE_MAX) {
	    throw new UnsupportedOptionsException();
	}

	// Block Header Size
	buf[0] = (byte) (buf.length / 4);

	// Write the Block Header field to the output stream.
	out.write(buf);
	EncoderUtil.writeCRC32(out, buf);

	// Calculate the maximum allowed size of the Compressed Data field.
	// It is hard to exceed it so this is mostly to be pedantic.
	this.compressedSizeLimit = (Util.VLI_MAX & ~3) - this.headerSize - check.getSize();
    }

    @Override
    public void finish() throws IOException {
	// Finish the Compressed Data field.
	this.filterChain.finish();
	this.validate();

	// Block Padding
	for (long i = this.outCounted.getSize(); (i & 3) != 0; ++i) {
	    this.out.write(0x00);
	}

	// Check
	this.out.write(this.check.finish());
    }

    @Override
    public void flush() throws IOException {
	this.filterChain.flush();
	this.validate();
    }

    public long getUncompressedSize() {
	return this.uncompressedSize;
    }

    public long getUnpaddedSize() {
	return this.headerSize + this.outCounted.getSize() + this.check.getSize();
    }

    private void validate() throws IOException {
	final long compressedSize = this.outCounted.getSize();

	// It is very hard to trigger this exception.
	// This is just to be pedantic.
	if (compressedSize < 0 || compressedSize > this.compressedSizeLimit || this.uncompressedSize < 0) {
	    throw new XZIOException("XZ Stream has grown too big");
	}
    }

    @Override
    public void write(final byte[] buf, final int off, final int len) throws IOException {
	this.filterChain.write(buf, off, len);
	this.check.update(buf, off, len);
	this.uncompressedSize += len;
	this.validate();
    }

    @Override
    public void write(final int b) throws IOException {
	this.tempBuf[0] = (byte) b;
	this.write(this.tempBuf, 0, 1);
    }
}
