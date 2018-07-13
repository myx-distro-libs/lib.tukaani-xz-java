/*
 * FilterOptions
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base class for filter-specific options classes.
 */
public abstract class FilterOptions implements Cloneable {
    /**
     * Gets how much memory the decoder will need with the given filter chain.
     * This function simply calls <code>getDecoderMemoryUsage()</code> for every
     * filter in the array and returns the sum of the returned values.
     */
    public static int getDecoderMemoryUsage(final FilterOptions[] options) {
	int m = 0;

	for (final FilterOptions option : options) {
	    m += option.getDecoderMemoryUsage();
	}

	return m;
    }

    /**
     * Gets how much memory the encoder will need with the given filter chain.
     * This function simply calls <code>getEncoderMemoryUsage()</code> for every
     * filter in the array and returns the sum of the returned values.
     */
    public static int getEncoderMemoryUsage(final FilterOptions[] options) {
	int m = 0;

	for (final FilterOptions option : options) {
	    m += option.getEncoderMemoryUsage();
	}

	return m;
    }

    FilterOptions() {
    }

    /**
     * Gets how much memory the decoder will need to decompress the data that
     * was encoded with these options.
     */
    public abstract int getDecoderMemoryUsage();

    /**
     * Gets how much memory the encoder will need with these options.
     */
    public abstract int getEncoderMemoryUsage();

    abstract FilterEncoder getFilterEncoder();

    /**
     * Gets a raw (no XZ headers) decoder input stream using these options.
     */
    public abstract InputStream getInputStream(InputStream in) throws IOException;

    /**
     * Gets a raw (no XZ headers) encoder output stream using these options. Raw
     * streams are an advanced feature. In most cases you want to store the
     * compressed data in the .xz container format instead of using a raw
     * stream. To use this filter in a .xz file, pass this object to
     * XZOutputStream.
     */
    public abstract FinishableOutputStream getOutputStream(FinishableOutputStream out);
}
