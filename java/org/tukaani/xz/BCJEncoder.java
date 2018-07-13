/*
 * BCJEncoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

class BCJEncoder extends BCJCoder implements FilterEncoder {
    private final BCJOptions options;
    private final long filterID;
    private final byte[] props;

    BCJEncoder(final BCJOptions options, final long filterID) {
	assert BCJCoder.isBCJFilterID(filterID);
	final int startOffset = options.getStartOffset();

	if (startOffset == 0) {
	    this.props = new byte[0];
	} else {
	    this.props = new byte[4];
	    for (int i = 0; i < 4; ++i) {
		this.props[i] = (byte) (startOffset >>> i * 8);
	    }
	}

	this.filterID = filterID;
	this.options = (BCJOptions) options.clone();
    }

    @Override
    public long getFilterID() {
	return this.filterID;
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
	return false;
    }
}
