/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class ChunkContainerOutputStream extends OutputStream {
    final private ChunkContainer container;
    private ByteArrayOutputStream outputStream;

    public ChunkContainerOutputStream(ChunkContainer container) {
        this.container = container;

        outputStream = new ByteArrayOutputStream();
    }

    @Override
    public void write(int i) throws IOException {
        outputStream.write(i);
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        byte[] data = outputStream.toByteArray();
        if (data.length == 0)
            return;
        container.append(new DataChunk(data));
        outputStream = new ByteArrayOutputStream();
    }

    public void startNewChunk() throws IOException {
        flush();
    }
}
