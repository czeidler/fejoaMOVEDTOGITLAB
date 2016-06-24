/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;


public class ChunkStore {
    /**
     * TODO: make the transaction actually do something, i.e. make a transaction atomic
     */
    public class Transaction {
        final private List<HashValue> objectsWritten = new ArrayList<>();
        final private ChunkStoreBranchLog log;

        public Transaction(File branchLogFile) throws IOException {
            this.log = new ChunkStoreBranchLog(branchLogFile);
        }

        public void put(HashValue hash, byte[] data) throws IOException {
            if (ChunkStore.this.put(hash, data))
                objectsWritten.add(hash);
        }

        public void commit(HashValue tip) throws IOException {
            synchronized (ChunkStore.this) {
                log.add(tip, objectsWritten);
                currentTransaction = null;
            }
        }
    }

    final private File dir;
    final private BPlusTree tree;
    final private PackFile packFile;
    private Transaction currentTransaction;

    protected ChunkStore(File dir, String name) throws FileNotFoundException {
        this.dir = dir;
        this.tree = new BPlusTree(new RandomAccessFile(new File(dir, name + ".idx"), "rw"));
        this.packFile = new PackFile(new RandomAccessFile(new File(dir, name + ".pack"), "rw"));
    }

    static public ChunkStore create(File dir, String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(dir, name);
        chunkStore.tree.create(hashSize(), 1024);
        chunkStore.packFile.create(hashSize());
        return chunkStore;
    }

    static public ChunkStore open(String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(new File("."), name);
        chunkStore.tree.open();
        chunkStore.packFile.open();
        return chunkStore;
    }

    public byte[] getChunk(String hash) throws IOException {
        return getChunk(CryptoHelper.fromHex(hash));
    }

    public byte[] getChunk(byte[] hash) throws IOException {
        Long position = tree.get(hash);
        if (position == null)
            return null;
        return packFile.get(position.intValue(), hash);
    }

    private File getBranchDir() {
        return new File(dir, "branches");
    }

    public Transaction openTransaction(String name) throws IOException {
        synchronized (this) {
            if (currentTransaction != null)
                throw new RuntimeException("Currently only one transaction at a time is supported");
            currentTransaction = new Transaction(new File(getBranchDir(), name));
            return currentTransaction;
        }
    }

    public ChunkStoreBranchLog getBranchLog(String name) throws IOException {
        return new ChunkStoreBranchLog(new File(getBranchDir(), name));
    }

    private boolean put(HashValue hash, byte[] data) throws IOException {
        if (hash.size() != hashSize())
            throw new IOException("Hash size miss match");
        long position = packFile.put(hash, data);
        return tree.put(hash, position);
    }

    static private int hashSize() {
        return 32;
    }

}
