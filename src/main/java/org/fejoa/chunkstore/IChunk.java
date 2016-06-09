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


public interface IChunk {
    HashValue hash();
    void read(DataInputStream inputStream) throws IOException;
    void write(DataOutputStream outputStream) throws IOException;
    byte[] getData() throws IOException;
    int getDataLength() throws IOException;
}
