/*
 * BlockInputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.tukaani.xz.check.Check;
import org.tukaani.xz.common.DecoderUtil;
import org.tukaani.xz.common.Util;

class BlockInputStream extends InputStream {
    private final DataInputStream inData;
    private final CountingInputStream inCounted;
    private InputStream filterChain;
    private final Check check;
    private final boolean verifyCheck;

    private long uncompressedSizeInHeader = -1;
    private long compressedSizeInHeader = -1;
    private long compressedSizeLimit;
    private final int headerSize;
    private long uncompressedSize = 0;
    private boolean endReached = false;

    private final byte[] tempBuf = new byte[1];

    public BlockInputStream(final InputStream in, final Check check, final boolean verifyCheck, final int memoryLimit,
	    final long unpaddedSizeInIndex, final long uncompressedSizeInIndex)
	    throws IOException, IndexIndicatorException {
	this.check = check;
	this.verifyCheck = verifyCheck;
	this.inData = new DataInputStream(in);

	final byte[] buf = new byte[Util.BLOCK_HEADER_SIZE_MAX];

	// Block Header Size or Index Indicator
	this.inData.readFully(buf, 0, 1);

	// See if this begins the Index field.
	if (buf[0] == 0x00) {
	    throw new IndexIndicatorException();
	}

	// Read the rest of the Block Header.
	this.headerSize = 4 * ((buf[0] & 0xFF) + 1);
	this.inData.readFully(buf, 1, this.headerSize - 1);

	// Validate the CRC32.
	if (!DecoderUtil.isCRC32Valid(buf, 0, this.headerSize - 4, this.headerSize - 4)) {
	    throw new CorruptedInputException("XZ Block Header is corrupt");
	}

	// Check for reserved bits in Block Flags.
	if ((buf[1] & 0x3C) != 0) {
	    throw new UnsupportedOptionsException("Unsupported options in XZ Block Header");
	}

	// Memory for the Filter Flags field
	final int filterCount = (buf[1] & 0x03) + 1;
	final long[] filterIDs = new long[filterCount];
	final byte[][] filterProps = new byte[filterCount][];

	// Use a stream to parse the fields after the Block Flags field.
	// Exclude the CRC32 field at the end.
	final ByteArrayInputStream bufStream = new ByteArrayInputStream(buf, 2, this.headerSize - 6);

	try {
	    // Set the maximum valid compressed size. This is overriden
	    // by the value from the Compressed Size field if it is present.
	    this.compressedSizeLimit = (Util.VLI_MAX & ~3) - this.headerSize - check.getSize();

	    // Decode and validate Compressed Size if the relevant flag
	    // is set in Block Flags.
	    if ((buf[1] & 0x40) != 0x00) {
		this.compressedSizeInHeader = DecoderUtil.decodeVLI(bufStream);

		if (this.compressedSizeInHeader == 0 || this.compressedSizeInHeader > this.compressedSizeLimit) {
		    throw new CorruptedInputException();
		}

		this.compressedSizeLimit = this.compressedSizeInHeader;
	    }

	    // Decode Uncompressed Size if the relevant flag is set
	    // in Block Flags.
	    if ((buf[1] & 0x80) != 0x00) {
		this.uncompressedSizeInHeader = DecoderUtil.decodeVLI(bufStream);
	    }

	    // Decode Filter Flags.
	    for (int i = 0; i < filterCount; ++i) {
		filterIDs[i] = DecoderUtil.decodeVLI(bufStream);

		final long filterPropsSize = DecoderUtil.decodeVLI(bufStream);
		if (filterPropsSize > bufStream.available()) {
		    throw new CorruptedInputException();
		}

		filterProps[i] = new byte[(int) filterPropsSize];
		bufStream.read(filterProps[i]);
	    }

	} catch (final IOException e) {
	    throw new CorruptedInputException("XZ Block Header is corrupt");
	}

	// Check that the remaining bytes are zero.
	for (int i = bufStream.available(); i > 0; --i) {
	    if (bufStream.read() != 0x00) {
		throw new UnsupportedOptionsException("Unsupported options in XZ Block Header");
	    }
	}

	// Validate the Blcok Header against the Index when doing
	// random access reading.
	if (unpaddedSizeInIndex != -1) {
	    // Compressed Data must be at least one byte, so if Block Header
	    // and Check alone take as much or more space than the size
	    // stored in the Index, the file is corrupt.
	    final int headerAndCheckSize = this.headerSize + check.getSize();
	    if (headerAndCheckSize >= unpaddedSizeInIndex) {
		throw new CorruptedInputException("XZ Index does not match a Block Header");
	    }

	    // The compressed size calculated from Unpadded Size must
	    // match the value stored in the Compressed Size field in
	    // the Block Header.
	    final long compressedSizeFromIndex = unpaddedSizeInIndex - headerAndCheckSize;
	    if (compressedSizeFromIndex > this.compressedSizeLimit
		    || this.compressedSizeInHeader != -1 && this.compressedSizeInHeader != compressedSizeFromIndex) {
		throw new CorruptedInputException("XZ Index does not match a Block Header");
	    }

	    // The uncompressed size stored in the Index must match
	    // the value stored in the Uncompressed Size field in
	    // the Block Header.
	    if (this.uncompressedSizeInHeader != -1 && this.uncompressedSizeInHeader != uncompressedSizeInIndex) {
		throw new CorruptedInputException("XZ Index does not match a Block Header");
	    }

	    // For further validation, pretend that the values from the Index
	    // were stored in the Block Header.
	    this.compressedSizeLimit = compressedSizeFromIndex;
	    this.compressedSizeInHeader = compressedSizeFromIndex;
	    this.uncompressedSizeInHeader = uncompressedSizeInIndex;
	}

	// Check if the Filter IDs are supported, decode
	// the Filter Properties, and check that they are
	// supported by this decoder implementation.
	final FilterDecoder[] filters = new FilterDecoder[filterIDs.length];

	for (int i = 0; i < filters.length; ++i) {
	    if (filterIDs[i] == LZMA2Coder.FILTER_ID) {
		filters[i] = new LZMA2Decoder(filterProps[i]);
	    } else if (filterIDs[i] == DeltaCoder.FILTER_ID) {
		filters[i] = new DeltaDecoder(filterProps[i]);
	    } else if (BCJCoder.isBCJFilterID(filterIDs[i])) {
		filters[i] = new BCJDecoder(filterIDs[i], filterProps[i]);
	    } else {
		throw new UnsupportedOptionsException("Unknown Filter ID " + filterIDs[i]);
	    }
	}

	RawCoder.validate(filters);

	// Check the memory usage limit.
	if (memoryLimit >= 0) {
	    int memoryNeeded = 0;
	    for (final FilterDecoder filter : filters) {
		memoryNeeded += filter.getMemoryUsage();
	    }

	    if (memoryNeeded > memoryLimit) {
		throw new MemoryLimitException(memoryNeeded, memoryLimit);
	    }
	}

	// Use an input size counter to calculate
	// the size of the Compressed Data field.
	this.inCounted = new CountingInputStream(in);

	// Initialize the filter chain.
	this.filterChain = this.inCounted;
	for (int i = filters.length - 1; i >= 0; --i) {
	    this.filterChain = filters[i].getInputStream(this.filterChain);
	}
    }

    @Override
    public int available() throws IOException {
	return this.filterChain.available();
    }

    public long getUncompressedSize() {
	return this.uncompressedSize;
    }

    public long getUnpaddedSize() {
	return this.headerSize + this.inCounted.getSize() + this.check.getSize();
    }

    @Override
    public int read() throws IOException {
	return this.read(this.tempBuf, 0, 1) == -1 ? -1 : this.tempBuf[0] & 0xFF;
    }

    @Override
    public int read(final byte[] buf, final int off, final int len) throws IOException {
	if (this.endReached) {
	    return -1;
	}

	final int ret = this.filterChain.read(buf, off, len);

	if (ret > 0) {
	    if (this.verifyCheck) {
		this.check.update(buf, off, ret);
	    }

	    this.uncompressedSize += ret;

	    // Catch invalid values.
	    final long compressedSize = this.inCounted.getSize();
	    if (compressedSize < 0 || compressedSize > this.compressedSizeLimit || this.uncompressedSize < 0
		    || this.uncompressedSizeInHeader != -1 && this.uncompressedSize > this.uncompressedSizeInHeader) {
		throw new CorruptedInputException();
	    }

	    // Check the Block integrity as soon as possible:
	    // - The filter chain shouldn't return less than requested
	    // unless it hit the end of the input.
	    // - If the uncompressed size is known, we know when there
	    // shouldn't be more data coming. We still need to read
	    // one byte to let the filter chain catch errors and to
	    // let it read end of payload marker(s).
	    if (ret < len || this.uncompressedSize == this.uncompressedSizeInHeader) {
		if (this.filterChain.read() != -1) {
		    throw new CorruptedInputException();
		}

		this.validate();
		this.endReached = true;
	    }
	} else if (ret == -1) {
	    this.validate();
	    this.endReached = true;
	}

	return ret;
    }

    private void validate() throws IOException {
	long compressedSize = this.inCounted.getSize();

	// Validate Compressed Size and Uncompressed Size if they were
	// present in Block Header.
	if (this.compressedSizeInHeader != -1 && this.compressedSizeInHeader != compressedSize
		|| this.uncompressedSizeInHeader != -1 && this.uncompressedSizeInHeader != this.uncompressedSize) {
	    throw new CorruptedInputException();
	}

	// Block Padding bytes must be zeros.
	while ((compressedSize++ & 3) != 0) {
	    if (this.inData.readUnsignedByte() != 0x00) {
		throw new CorruptedInputException();
	    }
	}

	// Validate the integrity check if verifyCheck is true.
	final byte[] storedCheck = new byte[this.check.getSize()];
	this.inData.readFully(storedCheck);
	if (this.verifyCheck && !Arrays.equals(this.check.finish(), storedCheck)) {
	    throw new CorruptedInputException("Integrity check (" + this.check.getName() + ") does not match");
	}
    }
}
