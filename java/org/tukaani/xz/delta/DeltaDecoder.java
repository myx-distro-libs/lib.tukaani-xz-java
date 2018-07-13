/*
 * DeltaDecoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.delta;

public class DeltaDecoder extends DeltaCoder {
    public DeltaDecoder(final int distance) {
	super(distance);
    }

    public void decode(final byte[] buf, final int off, final int len) {
	final int end = off + len;
	for (int i = off; i < end; ++i) {
	    buf[i] += this.history[this.distance + this.pos & DeltaCoder.DISTANCE_MASK];
	    this.history[this.pos-- & DeltaCoder.DISTANCE_MASK] = buf[i];
	}
    }
}
