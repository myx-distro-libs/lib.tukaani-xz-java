/*
 * PowerPCOptions
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

import org.tukaani.xz.simple.PowerPC;

/**
 * BCJ filter for big endian PowerPC instructions.
 */
public class PowerPCOptions extends BCJOptions {
    private static final int ALIGNMENT = 4;

    public PowerPCOptions() {
	super(PowerPCOptions.ALIGNMENT);
    }

    @Override
    FilterEncoder getFilterEncoder() {
	return new BCJEncoder(this, BCJCoder.POWERPC_FILTER_ID);
    }

    @Override
    public InputStream getInputStream(final InputStream in) {
	return new SimpleInputStream(in, new PowerPC(false, this.startOffset));
    }

    @Override
    public FinishableOutputStream getOutputStream(final FinishableOutputStream out) {
	return new SimpleOutputStream(out, new PowerPC(true, this.startOffset));
    }
}
