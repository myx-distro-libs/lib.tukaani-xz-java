/*
 * BCJOptions
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

abstract class BCJOptions extends FilterOptions {
    private final int alignment;
    int startOffset = 0;

    BCJOptions(final int alignment) {
	this.alignment = alignment;
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
	return SimpleInputStream.getMemoryUsage();
    }

    @Override
    public int getEncoderMemoryUsage() {
	return SimpleOutputStream.getMemoryUsage();
    }

    /**
     * Gets the start offset.
     */
    public int getStartOffset() {
	return this.startOffset;
    }

    /**
     * Sets the start offset for the address conversions. Normally this is
     * useless so you shouldn't use this function. The default value is
     * <code>0</code>.
     */
    public void setStartOffset(final int startOffset) throws UnsupportedOptionsException {
	if ((startOffset & this.alignment - 1) != 0) {
	    throw new UnsupportedOptionsException("Start offset must be a multiple of " + this.alignment);
	}

	this.startOffset = startOffset;
    }
}
