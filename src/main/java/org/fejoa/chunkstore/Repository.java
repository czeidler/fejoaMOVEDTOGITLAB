/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Repository {
    final private String branch;
    private IChunkAccessor blobAccessor;
    private boolean transactionStarted = false;

    public Repository(String branch, IChunkAccessor blobAccessor) throws IOException {
        this.branch = branch;
        this.blobAccessor = blobAccessor;

        ensureTransaction();
    }

    public IChunkAccessor getBlobAccessor() {
        return blobAccessor;
    }

    private void ensureTransaction() throws IOException {
        synchronized (this) {
            if (transactionStarted)
                return;
            blobAccessor.startTransaction(branch);
            transactionStarted = true;
        }
    }

    public TypedBlob get(HashValue hashValue) throws IOException {
        ChunkContainer chunkContainer = new ChunkContainer(blobAccessor, hashValue);
        BlobReader blobReader = new BlobReader(new ChunkContainerInputStream(chunkContainer));
        return blobReader.read(blobAccessor);
    }

    public HashValue put(TypedBlob blob) throws IOException {
        ensureTransaction();

        ChunkContainer chunkContainer = new ChunkContainer(blobAccessor);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.hash();
    }

    public void commit(HashValue tip) throws IOException {
        synchronized (Repository.this) {
            blobAccessor.finishTransaction(tip);
            transactionStarted = false;
        }
    }
}
