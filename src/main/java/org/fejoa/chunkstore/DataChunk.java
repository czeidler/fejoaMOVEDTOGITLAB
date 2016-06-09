/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.StreamHelper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class DataChunk implements IChunk {
    protected byte[] data;

    public DataChunk() {
    }

    public DataChunk(byte[] data) {
        this.data = data;
    }

    @Override
    public HashValue hash() {
        return new HashValue(CryptoHelper.sha256Hash(data));
    }

    @Override
    public void read(DataInputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamHelper.copy(inputStream, outputStream);
        this.data = outputStream.toByteArray();
    }

    @Override
    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.write(data);
    }

    @Override
    public byte[] getData() throws IOException {
        return data;
    }

    @Override
    public int getDataLength() throws IOException {
        return data.length;
    }
}
