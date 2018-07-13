/*
 * ARMThumbOptions
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

import org.tukaani.xz.simple.ARMThumb;

/**
 * BCJ filter for little endian ARM-Thumb instructions.
 */
public class ARMThumbOptions extends BCJOptions {
    private static final int ALIGNMENT = 2;

    public ARMThumbOptions() {
	super(ARMThumbOptions.ALIGNMENT);
    }

    @Override
    FilterEncoder getFilterEncoder() {
	return new BCJEncoder(this, BCJCoder.ARMTHUMB_FILTER_ID);
    }

    @Override
    public InputStream getInputStream(final InputStream in) {
	return new SimpleInputStream(in, new ARMThumb(false, this.startOffset));
    }

    @Override
    public FinishableOutputStream getOutputStream(final FinishableOutputStream out) {
	return new SimpleOutputStream(out, new ARMThumb(true, this.startOffset));
    }
}
