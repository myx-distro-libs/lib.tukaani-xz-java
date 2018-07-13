/*
 * IndexBase
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.index;

import org.tukaani.xz.XZIOException;
import org.tukaani.xz.common.Util;

abstract class IndexBase {
    private final XZIOException invalidIndexException;
    long blocksSum = 0;
    long uncompressedSum = 0;
    long indexListSize = 0;
    long recordCount = 0;

    IndexBase(final XZIOException invalidIndexException) {
	this.invalidIndexException = invalidIndexException;
    }

    void add(final long unpaddedSize, final long uncompressedSize) throws XZIOException {
	this.blocksSum += unpaddedSize + 3 & ~3;
	this.uncompressedSum += uncompressedSize;
	this.indexListSize += Util.getVLISize(unpaddedSize) + Util.getVLISize(uncompressedSize);
	++this.recordCount;

	if (this.blocksSum < 0 || this.uncompressedSum < 0 || this.getIndexSize() > Util.BACKWARD_SIZE_MAX
		|| this.getStreamSize() < 0) {
	    throw this.invalidIndexException;
	}
    }

    int getIndexPaddingSize() {
	return (int) (4 - this.getUnpaddedIndexSize() & 3);
    }

    public long getIndexSize() {
	return this.getUnpaddedIndexSize() + 3 & ~3;
    }

    public long getStreamSize() {
	return Util.STREAM_HEADER_SIZE + this.blocksSum + this.getIndexSize() + Util.STREAM_HEADER_SIZE;
    }

    private long getUnpaddedIndexSize() {
	// Index Indicator + Number of Records + List of Records + CRC32
	return 1 + Util.getVLISize(this.recordCount) + this.indexListSize + 4;
    }
}
