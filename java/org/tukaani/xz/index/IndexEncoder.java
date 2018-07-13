/*
 * IndexEncoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.index;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.CheckedOutputStream;

import org.tukaani.xz.XZIOException;
import org.tukaani.xz.common.EncoderUtil;

public class IndexEncoder extends IndexBase {
    private final ArrayList records = new ArrayList();

    public IndexEncoder() {
	super(new XZIOException("XZ Stream or its Index has grown too big"));
    }

    @Override
    public void add(final long unpaddedSize, final long uncompressedSize) throws XZIOException {
	super.add(unpaddedSize, uncompressedSize);
	this.records.add(new IndexRecord(unpaddedSize, uncompressedSize));
    }

    public void encode(final OutputStream out) throws IOException {
	final java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
	final CheckedOutputStream outChecked = new CheckedOutputStream(out, crc32);

	// Index Indicator
	outChecked.write(0x00);

	// Number of Records
	EncoderUtil.encodeVLI(outChecked, this.recordCount);

	// List of Records
	for (final Iterator i = this.records.iterator(); i.hasNext();) {
	    final IndexRecord record = (IndexRecord) i.next();
	    EncoderUtil.encodeVLI(outChecked, record.unpadded);
	    EncoderUtil.encodeVLI(outChecked, record.uncompressed);
	}

	// Index Padding
	for (int i = this.getIndexPaddingSize(); i > 0; --i) {
	    outChecked.write(0x00);
	}

	// CRC32
	final long value = crc32.getValue();
	for (int i = 0; i < 4; ++i) {
	    out.write((byte) (value >>> i * 8));
	}
    }
}
