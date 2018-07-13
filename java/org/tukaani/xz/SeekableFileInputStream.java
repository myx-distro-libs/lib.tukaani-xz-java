/*
 * SeekableFileInputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Wraps a {@link java.io.RandomAccessFile RandomAccessFile} in a
 * SeekableInputStream.
 */
public class SeekableFileInputStream extends SeekableInputStream {
    /**
     * The RandomAccessFile that has been wrapped into a
     * SeekableFileInputStream.
     */
    protected RandomAccessFile randomAccessFile;

    /**
     * Creates a new seekable input stream that reads from the specified file.
     */
    public SeekableFileInputStream(final File file) throws FileNotFoundException {
	this.randomAccessFile = new RandomAccessFile(file, "r");
    }

    /**
     * Creates a new seekable input stream from an existing
     * <code>RandomAccessFile</code> object.
     */
    public SeekableFileInputStream(final RandomAccessFile randomAccessFile) {
	this.randomAccessFile = randomAccessFile;
    }

    /**
     * Creates a new seekable input stream that reads from a file with the
     * specified name.
     */
    public SeekableFileInputStream(final String name) throws FileNotFoundException {
	this.randomAccessFile = new RandomAccessFile(name, "r");
    }

    /**
     * Calls {@link RandomAccessFile#close() randomAccessFile.close()}.
     */
    @Override
    public void close() throws IOException {
	this.randomAccessFile.close();
    }

    /**
     * Calls {@link RandomAccessFile#length() randomAccessFile.length()}.
     */
    @Override
    public long length() throws IOException {
	return this.randomAccessFile.length();
    }

    /**
     * Calls {@link RandomAccessFile#getFilePointer()
     * randomAccessFile.getFilePointer()}.
     */
    @Override
    public long position() throws IOException {
	return this.randomAccessFile.getFilePointer();
    }

    /**
     * Calls {@link RandomAccessFile#read() randomAccessFile.read()}.
     */
    @Override
    public int read() throws IOException {
	return this.randomAccessFile.read();
    }

    /**
     * Calls {@link RandomAccessFile#read(byte[]) randomAccessFile.read(buf)}.
     */
    @Override
    public int read(final byte[] buf) throws IOException {
	return this.randomAccessFile.read(buf);
    }

    /**
     * Calls {@link RandomAccessFile#read(byte[],int,int)
     * randomAccessFile.read(buf, off, len)}.
     */
    @Override
    public int read(final byte[] buf, final int off, final int len) throws IOException {
	return this.randomAccessFile.read(buf, off, len);
    }

    /**
     * Calls {@link RandomAccessFile#seek(long) randomAccessFile.seek(long)}.
     */
    @Override
    public void seek(final long pos) throws IOException {
	this.randomAccessFile.seek(pos);
    }
}
