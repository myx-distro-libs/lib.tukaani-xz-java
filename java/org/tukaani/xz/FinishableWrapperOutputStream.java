/*
 * FinishableWrapperOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Wraps an output stream to a finishable output stream for use with raw
 * encoders. This is not needed for XZ compression and thus most people will
 * never need this.
 */
public class FinishableWrapperOutputStream extends FinishableOutputStream {
    /**
     * The {@link java.io.OutputStream OutputStream} that has been wrapped into
     * a FinishableWrapperOutputStream.
     */
    protected OutputStream out;

    /**
     * Creates a new output stream which support finishing. The
     * <code>finish()</code> method will do nothing.
     */
    public FinishableWrapperOutputStream(final OutputStream out) {
	this.out = out;
    }

    /**
     * Calls {@link java.io.OutputStream#close() out.close()}.
     */
    @Override
    public void close() throws IOException {
	this.out.close();
    }

    /**
     * Calls {@link java.io.OutputStream#flush() out.flush()}.
     */
    @Override
    public void flush() throws IOException {
	this.out.flush();
    }

    /**
     * Calls {@link java.io.OutputStream#write(byte[]) out.write(buf)}.
     */
    @Override
    public void write(final byte[] buf) throws IOException {
	this.out.write(buf);
    }

    /**
     * Calls {@link java.io.OutputStream#write(byte[],int,int) out.write(buf,
     * off, len)}.
     */
    @Override
    public void write(final byte[] buf, final int off, final int len) throws IOException {
	this.out.write(buf, off, len);
    }

    /**
     * Calls {@link java.io.OutputStream#write(int) out.write(b)}.
     */
    @Override
    public void write(final int b) throws IOException {
	this.out.write(b);
    }
}
