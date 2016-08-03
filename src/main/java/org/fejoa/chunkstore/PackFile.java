/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;


public class PackFile {
    private short version = 1;
    private short hashSize;
    final private RandomAccessFile file;

    public PackFile(RandomAccessFile file) {
        this.file = file;
    }

    public void create(int hashSize) throws IOException {
        this.hashSize = (short)hashSize;

        file.setLength(0);
        writeHeader();
    }

    public void open() throws IOException {
        readHeader();
    }

    private void readHeader() throws IOException {
        file.seek(0);
        version = file.readShort();
        hashSize = file.readShort();
    }

    private void writeHeader() throws IOException {
        file.seek(0);
        file.writeShort(version);
        file.writeShort(hashSize);
    }

    private long headerSize() {
        return 2 * 4;
    }

    public long put(HashValue hash, byte[] data) throws IOException {
        long position = file.length();
        file.seek(position);
        file.write(hash.getBytes());
        file.writeInt(data.length);
        file.write(data);
        return position;
    }

    public byte[] get(int position, byte[] expectedHash) throws IOException {
        file.seek(position);
        byte[] hash = new byte[hashSize];
        file.readFully(hash);
        if (expectedHash != null && !Arrays.equals(expectedHash, hash))
            throw new IOException("Unexpected chunk at position " + position);

        int length = file.readInt();
        byte[] data = new byte[length];
        file.readFully(data);
        return data;
    }
}
