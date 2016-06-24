/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

abstract public class TypedBlob {
    final protected short type;

    protected TypedBlob(short type) {
        this.type = type;
    }

    public void read(DataInputStream inputStream) throws IOException {
        readInternal(inputStream);
    }

    abstract protected void readInternal(DataInputStream inputStream) throws IOException;
    abstract protected void writeInternal(DataOutputStream outputStream) throws IOException;

    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.writeShort(type);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteArrayOutputStream);
        writeInternal(dataOut);
        dataOut.close();
        byte[] data = byteArrayOutputStream.toByteArray();
        outputStream.write(data);
    }
}
