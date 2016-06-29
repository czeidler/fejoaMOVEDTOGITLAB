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
    DataInputStream getChunk(HashValue hash) throws IOException;
    PutResult<HashValue> putChunk(byte[] data) throws IOException;

    void startTransaction() throws IOException;
    void finishTransaction() throws IOException;
}
