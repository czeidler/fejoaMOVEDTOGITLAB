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
import java.io.IOException;

public class Repository {
    private IBlobAccessor blobAccessor;

    public Repository(IBlobAccessor blobAccessor) {
        this.blobAccessor = blobAccessor;
    }

    public IBlobAccessor getBlobAccessor() {
        return blobAccessor;
    }

    public HashValue put(TypedBlob blob) throws IOException {
        ChunkContainer chunkContainer = new ChunkContainer(blobAccessor);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.hash();
    }

    public TypedBlob get(HashValue hashValue) throws IOException {
        ChunkContainer chunkContainer = new ChunkContainer(blobAccessor, hashValue);
        BlobReader blobReader = new BlobReader(new ChunkContainerInputStream(chunkContainer));
        return blobReader.read(blobAccessor);
    }
}
