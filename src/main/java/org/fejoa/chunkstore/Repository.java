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
    private class Transaction {
        final private List<HashValue> objectsWritten = new ArrayList<>();
        final private ChunkStoreBranchLog log;

        public Transaction(File branchLogFile) throws IOException {
            this.log = new ChunkStoreBranchLog(branchLogFile);
        }

        public boolean put(HashValue hash, byte[] data) throws IOException {
            boolean added = chunkAccessor.putChunk(hash, data);
            if (added)
                objectsWritten.add(hash);

            return added;
        }

        public void commit(HashValue tip) throws IOException {
            synchronized (Repository.this) {
                chunkAccessor.finishTransaction();
                log.add(tip, objectsWritten);
                currentTransaction = null;
            }
        }
    }

    final private File dir;
    final private String branch;
    private IChunkAccessor chunkAccessor;
    private Transaction currentTransaction;
    private DirectoryBox rootDirectory = null;

    public Repository(File dir, String branch, IChunkAccessor chunkAccessor) throws IOException {
        this.dir = dir;
        this.branch = branch;
        this.chunkAccessor = chunkAccessor;

        ensureTransaction();
    }

    public IChunkAccessor getChunkAccessor() {
        return chunkAccessor;
    }

    private File getBranchDir() {
        return new File(dir, "branches");
    }

    private void ensureTransaction() throws IOException {
        synchronized (this) {
            if (currentTransaction != null)
                return;
            chunkAccessor.startTransaction();
            currentTransaction = new Transaction(new File(getBranchDir(), branch));
        }
    }

    public TypedBlob get(HashValue hashValue) throws IOException {
        ChunkContainer chunkContainer = new ChunkContainer(chunkAccessor, hashValue);
        BlobReader blobReader = new BlobReader(new ChunkContainerInputStream(chunkContainer));
        return blobReader.read(chunkAccessor);
    }

    public HashValue put(TypedBlob blob) throws IOException {
        ensureTransaction();

        ChunkContainer chunkContainer = new ChunkContainer(chunkAccessor);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.hash();
    }

    public byte[] readBytes(String path) throws IOException {
        return null;
    }

    public void writeBytes(String path, byte[] bytes) throws IOException {

    }

    public void commit(HashValue tip) throws IOException {
        synchronized (Repository.this) {
            currentTransaction.commit(tip);
            currentTransaction = null;
        }
    }

    public ChunkStoreBranchLog getBranchLog(String name) throws IOException {
        return new ChunkStoreBranchLog(new File(getBranchDir(), name));
    }
}
