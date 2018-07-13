/*
 * DeltaDecoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

class DeltaDecoder extends DeltaCoder implements FilterDecoder {
    private final int distance;

    DeltaDecoder(final byte[] props) throws UnsupportedOptionsException {
	if (props.length != 1) {
	    throw new UnsupportedOptionsException("Unsupported Delta filter properties");
	}

	this.distance = (props[0] & 0xFF) + 1;
    }

    @Override
    public InputStream getInputStream(final InputStream in) {
	return new DeltaInputStream(in, this.distance);
    }

    @Override
    public int getMemoryUsage() {
	return 1;
    }
}
