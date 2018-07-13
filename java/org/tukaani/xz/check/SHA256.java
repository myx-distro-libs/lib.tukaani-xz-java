/*
 * SHA256
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.check;

public class SHA256 extends Check {
    private final java.security.MessageDigest sha256;

    public SHA256() throws java.security.NoSuchAlgorithmException {
	this.size = 32;
	this.name = "SHA-256";
	this.sha256 = java.security.MessageDigest.getInstance("SHA-256");
    }

    @Override
    public byte[] finish() {
	final byte[] buf = this.sha256.digest();
	this.sha256.reset();
	return buf;
    }

    @Override
    public void update(final byte[] buf, final int off, final int len) {
	this.sha256.update(buf, off, len);
    }
}
