/*
 * CRC32
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.check;

public class CRC32 extends Check {
    private final java.util.zip.CRC32 state = new java.util.zip.CRC32();

    public CRC32() {
	this.size = 4;
	this.name = "CRC32";
    }

    @Override
    public byte[] finish() {
	final long value = this.state.getValue();
	final byte[] buf = { (byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24) };
	this.state.reset();
	return buf;
    }

    @Override
    public void update(final byte[] buf, final int off, final int len) {
	this.state.update(buf, off, len);
    }
}
