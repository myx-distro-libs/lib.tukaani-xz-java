/*
 * DeltaEncoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.delta;

public class DeltaEncoder extends DeltaCoder {
    public DeltaEncoder(final int distance) {
	super(distance);
    }

    public void encode(final byte[] in, final int in_off, final int len, final byte[] out) {
	for (int i = 0; i < len; ++i) {
	    final byte tmp = this.history[this.distance + this.pos & DeltaCoder.DISTANCE_MASK];
	    this.history[this.pos-- & DeltaCoder.DISTANCE_MASK] = in[in_off + i];
	    out[i] = (byte) (in[in_off + i] - tmp);
	}
    }
}
