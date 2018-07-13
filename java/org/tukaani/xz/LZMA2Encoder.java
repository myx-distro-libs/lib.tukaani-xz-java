/*
 * LZMA2Encoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import org.tukaani.xz.lzma.LZMAEncoder;

class LZMA2Encoder extends LZMA2Coder implements FilterEncoder {
    private final LZMA2Options options;
    private final byte[] props = new byte[1];

    LZMA2Encoder(final LZMA2Options options) {
	if (options.getPresetDict() != null) {
	    throw new IllegalArgumentException("XZ doesn't support a preset dictionary for now");
	}

	if (options.getMode() == LZMA2Options.MODE_UNCOMPRESSED) {
	    this.props[0] = (byte) 0;
	} else {
	    final int d = Math.max(options.getDictSize(), LZMA2Options.DICT_SIZE_MIN);
	    this.props[0] = (byte) (LZMAEncoder.getDistSlot(d - 1) - 23);
	}

	// Make a private copy so that the caller is free to change its copy.
	this.options = (LZMA2Options) options.clone();
    }

    @Override
    public long getFilterID() {
	return LZMA2Coder.FILTER_ID;
    }

    @Override
    public byte[] getFilterProps() {
	return this.props;
    }

    @Override
    public FinishableOutputStream getOutputStream(final FinishableOutputStream out) {
	return this.options.getOutputStream(out);
    }

    @Override
    public boolean supportsFlushing() {
	return true;
    }
}
