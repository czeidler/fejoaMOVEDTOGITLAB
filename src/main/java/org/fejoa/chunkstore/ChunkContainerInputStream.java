/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.io.InputStream;


public class ChunkContainerInputStream extends InputStream {
    final private ChunkContainer container;
    private long position = 0;
    private ChunkContainer.DataChunkPosition chunkPosition;

    public ChunkContainerInputStream(ChunkContainer container) {
        this.container = container;
    }

    @Override
    public int read() throws IOException {
        if (position >= container.getDataLength())
            return -1;
        DataChunk current;
        try {
            current = validateCurrentChunk();
        } catch (CryptoException e) {
            throw new IOException(e);
        }
        int b = current.getData()[(int)(position - chunkPosition.position)] & 0xff;
        position++;
        return b;
    }

    public void seek(long position) throws IOException {
        this.position = position;
        if (chunkPosition != null && (position >= chunkPosition.position + chunkPosition.chunk.getDataLength()
                    || position < chunkPosition.position)) {
                chunkPosition = null;
        }
    }

    private DataChunk validateCurrentChunk() throws IOException, CryptoException {
        if (chunkPosition != null && position < (chunkPosition.position + chunkPosition.chunk.getDataLength())) {
                return chunkPosition.chunk;
        }
        chunkPosition = container.get(position);
        if (chunkPosition.chunk == null) {
            position = -1;
            chunkPosition = null;
            throw new IOException("Invalid position");
        }
        return chunkPosition.chunk;
    }
}
