/*
 * DeltaEncoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

class DeltaEncoder extends DeltaCoder implements FilterEncoder {
    private final DeltaOptions options;
    private final byte[] props = new byte[1];

    DeltaEncoder(final DeltaOptions options) {
	this.props[0] = (byte) (options.getDistance() - 1);
	this.options = (DeltaOptions) options.clone();
    }

    @Override
    public long getFilterID() {
	return DeltaCoder.FILTER_ID;
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
