/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StreamHelper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;


public class FileBox {
    private IChunkAccessor accessor;
    private ChunkContainer dataContainer;

    static public FileBox create(IChunkAccessor accessor, ChunkSplitter nodeSplitter) {
        FileBox fileBox = new FileBox();
        fileBox.accessor = accessor;
        fileBox.dataContainer = new ChunkContainer(accessor, nodeSplitter);
        return fileBox;
    }

    static public FileBox read(IChunkAccessor accessor, BoxPointer pointer)
            throws IOException, CryptoException {
        FileBox fileBox = new FileBox();
        fileBox.accessor = accessor;
        fileBox.read(accessor.getChunk(pointer));
        return fileBox;
    }

    public void flush() throws IOException, CryptoException {
        dataContainer.flush(false);
    }

    private void read(DataInputStream inputStream) throws IOException {
        dataContainer = new ChunkContainer(accessor, inputStream);
    }

    public ChunkContainer getDataContainer() {
        return dataContainer;
    }

    @Override
    public String toString() {
        if (dataContainer == null)
            return "empty";
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(dataContainer);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            StreamHelper.copy(inputStream, outputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return "invalid";
        }
        return "FileBox: " + new String(outputStream.toByteArray());
    }
}
