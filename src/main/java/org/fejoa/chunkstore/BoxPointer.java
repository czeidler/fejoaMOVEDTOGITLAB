/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class BoxPointer {
    private HashValue dataHash;
    private HashValue boxHash;

    public BoxPointer() {

    }

    public BoxPointer(HashValue data, HashValue box) {
        this.dataHash = data;
        this.boxHash = box;
    }

    public HashValue getDataHash() {
        return dataHash;
    }

    public HashValue getBoxHash() {
        return boxHash;
    }

    public void read(DataInputStream inputStream) throws IOException {
        HashValue data = new HashValue(HashValue.HASH_SIZE);
        HashValue box = new HashValue(HashValue.HASH_SIZE);
        inputStream.readFully(data.getBytes());
        inputStream.readFully(box.getBytes());
        this.dataHash = data;
        this.boxHash = box;
    }

    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.write(dataHash.getBytes());
        outputStream.write(boxHash.getBytes());
    }

    @Override
    public String toString() {
        return "data: " + dataHash.toString() + "box: " + boxHash.toString();
    }
}
