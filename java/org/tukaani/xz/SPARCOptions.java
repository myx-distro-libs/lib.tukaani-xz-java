/*
 * SPARCOptions
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

import org.tukaani.xz.simple.SPARC;

/**
 * BCJ filter for SPARC.
 */
public class SPARCOptions extends BCJOptions {
    private static final int ALIGNMENT = 4;

    public SPARCOptions() {
	super(SPARCOptions.ALIGNMENT);
    }

    @Override
    FilterEncoder getFilterEncoder() {
	return new BCJEncoder(this, BCJCoder.SPARC_FILTER_ID);
    }

    @Override
    public InputStream getInputStream(final InputStream in) {
	return new SimpleInputStream(in, new SPARC(false, this.startOffset));
    }

    @Override
    public FinishableOutputStream getOutputStream(final FinishableOutputStream out) {
	return new SimpleOutputStream(out, new SPARC(true, this.startOffset));
    }
}
