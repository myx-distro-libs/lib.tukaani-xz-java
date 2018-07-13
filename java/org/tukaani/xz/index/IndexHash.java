/*
 * IndexHash
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.index;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CheckedInputStream;

import org.tukaani.xz.CorruptedInputException;
import org.tukaani.xz.XZIOException;
import org.tukaani.xz.common.DecoderUtil;

public class IndexHash extends IndexBase {
    private org.tukaani.xz.check.Check hash;

    public IndexHash() {
	super(new CorruptedInputException());

	try {
	    this.hash = new org.tukaani.xz.check.SHA256();
	} catch (final java.security.NoSuchAlgorithmException e) {
	    this.hash = new org.tukaani.xz.check.CRC32();
	}
    }

    @Override
    public void add(final long unpaddedSize, final long uncompressedSize) throws XZIOException {
	super.add(unpaddedSize, uncompressedSize);

	final ByteBuffer buf = ByteBuffer.allocate(2 * 8);
	buf.putLong(unpaddedSize);
	buf.putLong(uncompressedSize);
	this.hash.update(buf.array());
    }

    public void validate(final InputStream in) throws IOException {
	// Index Indicator (0x00) has already been read by BlockInputStream
	// so add 0x00 to the CRC32 here.
	final java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
	crc32.update('\0');
	final CheckedInputStream inChecked = new CheckedInputStream(in, crc32);

	// Get and validate the Number of Records field.
	// If Block Header Size was corrupt and became Index Indicator,
	// this error would actually be about corrupt Block Header.
	// This is why the error message mentions both possibilities.
	final long storedRecordCount = DecoderUtil.decodeVLI(inChecked);
	if (storedRecordCount != this.recordCount) {
	    throw new CorruptedInputException("XZ Block Header or the start of XZ Index is corrupt");
	}

	// Decode and hash the Index field and compare it to
	// the hash value calculated from the decoded Blocks.
	final IndexHash stored = new IndexHash();
	for (long i = 0; i < this.recordCount; ++i) {
	    final long unpaddedSize = DecoderUtil.decodeVLI(inChecked);
	    final long uncompressedSize = DecoderUtil.decodeVLI(inChecked);

	    try {
		stored.add(unpaddedSize, uncompressedSize);
	    } catch (final XZIOException e) {
		throw new CorruptedInputException("XZ Index is corrupt");
	    }

	    if (stored.blocksSum > this.blocksSum || stored.uncompressedSum > this.uncompressedSum
		    || stored.indexListSize > this.indexListSize) {
		throw new CorruptedInputException("XZ Index is corrupt");
	    }
	}

	if (stored.blocksSum != this.blocksSum || stored.uncompressedSum != this.uncompressedSum
		|| stored.indexListSize != this.indexListSize
		|| !Arrays.equals(stored.hash.finish(), this.hash.finish())) {
	    throw new CorruptedInputException("XZ Index is corrupt");
	}

	// Index Padding
	final DataInputStream inData = new DataInputStream(inChecked);
	for (int i = this.getIndexPaddingSize(); i > 0; --i) {
	    if (inData.readUnsignedByte() != 0x00) {
		throw new CorruptedInputException("XZ Index is corrupt");
	    }
	}

	// CRC32
	final long value = crc32.getValue();
	for (int i = 0; i < 4; ++i) {
	    if ((value >>> i * 8 & 0xFF) != inData.readUnsignedByte()) {
		throw new CorruptedInputException("XZ Index is corrupt");
	    }
	}
    }
}
