/*
 * DeltaCoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

abstract class DeltaCoder implements FilterCoder {
    public static final long FILTER_ID = 0x03;

    @Override
    public boolean changesSize() {
	return false;
    }

    @Override
    public boolean lastOK() {
	return false;
    }

    @Override
    public boolean nonLastOK() {
	return true;
    }
}
