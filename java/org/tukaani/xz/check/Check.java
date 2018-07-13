/*
 * Check
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.check;

import org.tukaani.xz.UnsupportedOptionsException;
import org.tukaani.xz.XZ;

public abstract class Check {
    public static Check getInstance(final int checkType) throws UnsupportedOptionsException {
	switch (checkType) {
	case XZ.CHECK_NONE:
	    return new None();

	case XZ.CHECK_CRC32:
	    return new CRC32();

	case XZ.CHECK_CRC64:
	    return new CRC64();

	case XZ.CHECK_SHA256:
	    try {
		return new SHA256();
	    } catch (final java.security.NoSuchAlgorithmException e) {
	    }

	    break;
	}

	throw new UnsupportedOptionsException("Unsupported Check ID " + checkType);
    }

    int size;

    String name;

    public abstract byte[] finish();

    public String getName() {
	return this.name;
    }

    public int getSize() {
	return this.size;
    }

    public void update(final byte[] buf) {
	this.update(buf, 0, buf.length);
    }

    public abstract void update(byte[] buf, int off, int len);
}
