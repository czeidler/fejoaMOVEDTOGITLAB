/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;

import java.io.DataInputStream;
import java.io.IOException;


public interface IChunkAccessor {
    DataInputStream getChunk(BoxPointer hash) throws IOException, CryptoException;
    PutResult<HashValue> putChunk(byte[] data) throws IOException, CryptoException;
}
