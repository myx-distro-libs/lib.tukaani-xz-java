/*
 * 2-, 3-, and 4-byte hashing
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lz;

final class Hash234 extends CRC32Hash {
    private static final int HASH_2_SIZE = 1 << 10;
    private static final int HASH_2_MASK = Hash234.HASH_2_SIZE - 1;

    private static final int HASH_3_SIZE = 1 << 16;
    private static final int HASH_3_MASK = Hash234.HASH_3_SIZE - 1;

    static int getHash4Size(final int dictSize) {
	int h = dictSize - 1;
	h |= h >>> 1;
	h |= h >>> 2;
	h |= h >>> 4;
	h |= h >>> 8;
	h >>>= 1;
	h |= 0xFFFF;
	if (h > 1 << 24) {
	    h >>>= 1;
	}

	return h + 1;
    }

    static int getMemoryUsage(final int dictSize) {
	// Sizes of the hash arrays + a little extra
	return (Hash234.HASH_2_SIZE + Hash234.HASH_3_SIZE + Hash234.getHash4Size(dictSize)) / (1024 / 4) + 4;
    }

    private final int hash4Mask;
    private final int[] hash2Table = new int[Hash234.HASH_2_SIZE];

    private final int[] hash3Table = new int[Hash234.HASH_3_SIZE];
    private final int[] hash4Table;
    private int hash2Value = 0;

    private int hash3Value = 0;

    private int hash4Value = 0;

    Hash234(final int dictSize) {
	this.hash4Table = new int[Hash234.getHash4Size(dictSize)];
	this.hash4Mask = this.hash4Table.length - 1;
    }

    void calcHashes(final byte[] buf, final int off) {
	int temp = CRC32Hash.crcTable[buf[off] & 0xFF] ^ buf[off + 1] & 0xFF;
	this.hash2Value = temp & Hash234.HASH_2_MASK;

	temp ^= (buf[off + 2] & 0xFF) << 8;
	this.hash3Value = temp & Hash234.HASH_3_MASK;

	temp ^= CRC32Hash.crcTable[buf[off + 3] & 0xFF] << 5;
	this.hash4Value = temp & this.hash4Mask;
    }

    int getHash2Pos() {
	return this.hash2Table[this.hash2Value];
    }

    int getHash3Pos() {
	return this.hash3Table[this.hash3Value];
    }

    int getHash4Pos() {
	return this.hash4Table[this.hash4Value];
    }

    void normalize(final int normalizeOffset) {
	LZEncoder.normalize(this.hash2Table, normalizeOffset);
	LZEncoder.normalize(this.hash3Table, normalizeOffset);
	LZEncoder.normalize(this.hash4Table, normalizeOffset);
    }

    void updateTables(final int pos) {
	this.hash2Table[this.hash2Value] = pos;
	this.hash3Table[this.hash3Value] = pos;
	this.hash4Table[this.hash4Value] = pos;
    }
}
