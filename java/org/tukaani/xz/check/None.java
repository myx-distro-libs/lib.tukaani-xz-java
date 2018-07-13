/*
 * None
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.check;

public class None extends Check {
    public None() {
	this.size = 0;
	this.name = "None";
    }

    @Override
    public byte[] finish() {
	final byte[] empty = new byte[0];
	return empty;
    }

    @Override
    public void update(final byte[] buf, final int off, final int len) {
    }
}
