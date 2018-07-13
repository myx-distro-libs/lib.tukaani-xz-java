/*
 * X86Options
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

import org.tukaani.xz.simple.X86;

/**
 * BCJ filter for x86 (32-bit and 64-bit) instructions.
 */
public class X86Options extends BCJOptions {
    private static final int ALIGNMENT = 1;

    public X86Options() {
	super(X86Options.ALIGNMENT);
    }

    @Override
    FilterEncoder getFilterEncoder() {
	return new BCJEncoder(this, BCJCoder.X86_FILTER_ID);
    }

    @Override
    public InputStream getInputStream(final InputStream in) {
	return new SimpleInputStream(in, new X86(false, this.startOffset));
    }

    @Override
    public FinishableOutputStream getOutputStream(final FinishableOutputStream out) {
	return new SimpleOutputStream(out, new X86(true, this.startOffset));
    }
}
