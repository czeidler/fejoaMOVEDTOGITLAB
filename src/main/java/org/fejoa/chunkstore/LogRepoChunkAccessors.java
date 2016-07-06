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
import java.util.ArrayList;
import java.util.List;


public class LogRepoChunkAccessors implements IRepoChunkAccessors {
    final private IRepoChunkAccessors childAccessors;
    final private List<HashValue> objectsWritten = new ArrayList<>();

    public LogRepoChunkAccessors(IRepoChunkAccessors accessors) {
        this.childAccessors = accessors;
    }

    @Override
    public IChunkAccessor getCommitAccessor() {
        return createWrapper(childAccessors.getCommitAccessor());
    }

    @Override
    public IChunkAccessor getTreeAccessor() {
        return createWrapper(childAccessors.getTreeAccessor());
    }

    @Override
    public IChunkAccessor getFileAccessor(String filePath) {
        return createWrapper(childAccessors.getFileAccessor(filePath));
    }

    private IChunkAccessor createWrapper(final IChunkAccessor chunkAccessor) {
        return new IChunkAccessor() {
            @Override
            public DataInputStream getChunk(BoxPointer hash) throws IOException, CryptoException {
                return null;
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data) throws IOException, CryptoException {
                PutResult<HashValue> result = chunkAccessor.putChunk(data);
                if (!result.wasInDatabase)
                    objectsWritten.add(result.key);

                return result;
            }

            @Override
            public void releaseChunk(HashValue data) {
                for (HashValue written : objectsWritten) {
                    if (!written.equals(data))
                        continue;
                    objectsWritten.remove(written);
                    break;
                }
            }
        };
    }

    @Override
    public void startTransaction() throws IOException {
        objectsWritten.clear();
        childAccessors.startTransaction();
    }

    @Override
    public void finishTransaction() throws IOException {
        childAccessors.finishTransaction();
    }

    public List<HashValue> getObjectsWritten() {
        return objectsWritten;
    }
}
