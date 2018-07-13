/*
 * LZMA2Coder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

abstract class LZMA2Coder implements FilterCoder {
    public static final long FILTER_ID = 0x21;

    @Override
    public boolean changesSize() {
	return true;
    }

    @Override
    public boolean lastOK() {
	return true;
    }

    @Override
    public boolean nonLastOK() {
	return false;
    }
}
