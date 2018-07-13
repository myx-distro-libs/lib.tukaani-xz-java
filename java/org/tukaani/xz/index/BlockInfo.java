/*
 * BlockInfo
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.index;

public class BlockInfo {
    public int blockNumber = -1;
    public long compressedOffset = -1;
    public long uncompressedOffset = -1;
    public long unpaddedSize = -1;
    public long uncompressedSize = -1;

    IndexDecoder index;

    public BlockInfo(final IndexDecoder indexOfFirstStream) {
	this.index = indexOfFirstStream;
    }

    public int getCheckType() {
	return this.index.getStreamFlags().checkType;
    }

    public boolean hasNext() {
	return this.index.hasRecord(this.blockNumber + 1);
    }

    public void setNext() {
	this.index.setBlockInfo(this, this.blockNumber + 1);
    }
}
