/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.DataInputStream;
import java.io.IOException;


public interface IChunkAccessor {
    DataInputStream getBlob(HashValue hash) throws IOException;
    void putChunk(HashValue hash, byte[] data) throws IOException;
    HashValue putChunk(IChunk blob) throws IOException;

    void startTransaction(String name) throws IOException;
    void finishTransaction(HashValue tip) throws IOException;
}
