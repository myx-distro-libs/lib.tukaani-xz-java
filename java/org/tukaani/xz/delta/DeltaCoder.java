/*
 * DeltaCoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.delta;

abstract class DeltaCoder {
    static final int DISTANCE_MIN = 1;
    static final int DISTANCE_MAX = 256;
    static final int DISTANCE_MASK = DeltaCoder.DISTANCE_MAX - 1;

    final int distance;
    final byte[] history = new byte[DeltaCoder.DISTANCE_MAX];
    int pos = 0;

    DeltaCoder(final int distance) {
	if (distance < DeltaCoder.DISTANCE_MIN || distance > DeltaCoder.DISTANCE_MAX) {
	    throw new IllegalArgumentException();
	}

	this.distance = distance;
    }
}
