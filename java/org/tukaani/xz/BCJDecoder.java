/*
 * BCJDecoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;

import org.tukaani.xz.simple.ARM;
import org.tukaani.xz.simple.ARMThumb;
import org.tukaani.xz.simple.IA64;
import org.tukaani.xz.simple.PowerPC;
import org.tukaani.xz.simple.SPARC;
import org.tukaani.xz.simple.SimpleFilter;
import org.tukaani.xz.simple.X86;

class BCJDecoder extends BCJCoder implements FilterDecoder {
    private final long filterID;
    private final int startOffset;

    BCJDecoder(final long filterID, final byte[] props) throws UnsupportedOptionsException {
	assert BCJCoder.isBCJFilterID(filterID);
	this.filterID = filterID;

	if (props.length == 0) {
	    this.startOffset = 0;
	} else if (props.length == 4) {
	    int n = 0;
	    for (int i = 0; i < 4; ++i) {
		n |= (props[i] & 0xFF) << i * 8;
	    }

	    this.startOffset = n;
	} else {
	    throw new UnsupportedOptionsException("Unsupported BCJ filter properties");
	}
    }

    @Override
    public InputStream getInputStream(final InputStream in) {
	SimpleFilter simpleFilter = null;

	if (this.filterID == BCJCoder.X86_FILTER_ID) {
	    simpleFilter = new X86(false, this.startOffset);
	} else if (this.filterID == BCJCoder.POWERPC_FILTER_ID) {
	    simpleFilter = new PowerPC(false, this.startOffset);
	} else if (this.filterID == BCJCoder.IA64_FILTER_ID) {
	    simpleFilter = new IA64(false, this.startOffset);
	} else if (this.filterID == BCJCoder.ARM_FILTER_ID) {
	    simpleFilter = new ARM(false, this.startOffset);
	} else if (this.filterID == BCJCoder.ARMTHUMB_FILTER_ID) {
	    simpleFilter = new ARMThumb(false, this.startOffset);
	} else if (this.filterID == BCJCoder.SPARC_FILTER_ID) {
	    simpleFilter = new SPARC(false, this.startOffset);
	} else {
	    assert false;
	}

	return new SimpleInputStream(in, simpleFilter);
    }

    @Override
    public int getMemoryUsage() {
	return SimpleInputStream.getMemoryUsage();
    }
}
