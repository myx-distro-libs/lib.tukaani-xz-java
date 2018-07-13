/*
 * RawCoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

class RawCoder {
    static void validate(final FilterCoder[] filters) throws UnsupportedOptionsException {
	for (int i = 0; i < filters.length - 1; ++i) {
	    if (!filters[i].nonLastOK()) {
		throw new UnsupportedOptionsException("Unsupported XZ filter chain");
	    }
	}

	if (!filters[filters.length - 1].lastOK()) {
	    throw new UnsupportedOptionsException("Unsupported XZ filter chain");
	}

	int changesSizeCount = 0;
	for (final FilterCoder filter : filters) {
	    if (filter.changesSize()) {
		++changesSizeCount;
	    }
	}

	if (changesSizeCount > 3) {
	    throw new UnsupportedOptionsException("Unsupported XZ filter chain");
	}
    }
}
