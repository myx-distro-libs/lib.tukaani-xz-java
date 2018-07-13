/*
 * DeltaOptions
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

/**
 * Delta filter options. The Delta filter can be used only as a non-last filter
 * in the chain, for example Delta + LZMA2.
 * <p>
 * Currently only simple byte-wise delta is supported. The only option is the
 * delta distance, which you should set to match your data. It's not possible to
 * provide a generic default value for it.
 * <p>
 * For example, with distance = 2 and eight-byte input A1 B1 A2 B3 A3 B5 A4 B7,
 * the output will be A1 B1 01 02 01 02 01 02.
 * <p>
 * The Delta filter can be good with uncompressed bitmap images. It can also
 * help with PCM audio, although special-purpose compressors like FLAC will give
 * much smaller result at much better compression speed.
 */
public class DeltaOptions extends FilterOptions {
    /**
     * Smallest supported delta calculation distance.
     */
    public static final int DISTANCE_MIN = 1;

    /**
     * Largest supported delta calculation distance.
     */
    public static final int DISTANCE_MAX = 256;

    private int distance = DeltaOptions.DISTANCE_MIN;

    /**
     * Creates new Delta options and sets the delta distance to 1 byte.
     */
    public DeltaOptions() {
    }

    /**
     * Creates new Delta options and sets the distance to the given value.
     */
    public DeltaOptions(final int distance) throws UnsupportedOptionsException {
	this.setDistance(distance);
    }

    @Override
    public Object clone() {
	try {
	    return super.clone();
	} catch (final CloneNotSupportedException e) {
	    assert false;
	    throw new RuntimeException();
	}
    }

    @Override
    public int getDecoderMemoryUsage() {
	return 1;
    }

    /**
     * Gets the delta distance.
     */
    public int getDistance() {
	return this.distance;
    }

    @Override
    public int getEncoderMemoryUsage() {
	return DeltaOutputStream.getMemoryUsage();
    }

    @Override
    FilterEncoder getFilterEncoder() {
	return new DeltaEncoder(this);
    }

    @Override
    public InputStream getInputStream(final InputStream in) {
	return new DeltaInputStream(in, this.distance);
    }

    @Override
    public FinishableOutputStream getOutputStream(final FinishableOutputStream out) {
	return new DeltaOutputStream(out, this);
    }

    /**
     * Sets the delta distance in bytes. The new distance must be in the range
     * [DISTANCE_MIN, DISTANCE_MAX].
     */
    public void setDistance(final int distance) throws UnsupportedOptionsException {
	if (distance < DeltaOptions.DISTANCE_MIN || distance > DeltaOptions.DISTANCE_MAX) {
	    throw new UnsupportedOptionsException("Delta distance must be in the range [" + DeltaOptions.DISTANCE_MIN
		    + ", " + DeltaOptions.DISTANCE_MAX + "]: " + distance);
	}

	this.distance = distance;
    }
}
