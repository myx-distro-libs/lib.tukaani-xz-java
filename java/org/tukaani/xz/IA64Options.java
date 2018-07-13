/*
 * IA64Options
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

import org.tukaani.xz.simple.IA64;

/**
 * BCJ filter for Itanium (IA-64) instructions.
 */
public class IA64Options extends BCJOptions {
    private static final int ALIGNMENT = 16;

    public IA64Options() {
	super(IA64Options.ALIGNMENT);
    }

    @Override
    FilterEncoder getFilterEncoder() {
	return new BCJEncoder(this, BCJCoder.IA64_FILTER_ID);
    }

    @Override
    public InputStream getInputStream(final InputStream in) {
	return new SimpleInputStream(in, new IA64(false, this.startOffset));
    }

    @Override
    public FinishableOutputStream getOutputStream(final FinishableOutputStream out) {
	return new SimpleOutputStream(out, new IA64(true, this.startOffset));
    }
}
