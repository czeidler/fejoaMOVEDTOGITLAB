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


public class ChunkStore {
    /**
     * TODO: make the transaction actually do something, i.e. make a transaction atomic
     */
    public class Transaction {
        /**
         *
         * @param hash
         * @param data
         * @return false if hash is already in the database
         * @throws IOException
         */
        public boolean put(HashValue hash, byte[] data) throws IOException {
            return ChunkStore.this.put(hash, data);
        }

        public void commit() throws IOException {
            currentTransaction = null;
        }
    }

    final private BPlusTree tree;
    final private PackFile packFile;
    private Transaction currentTransaction;

    protected ChunkStore(File dir, String name) throws FileNotFoundException {
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



    public Transaction openTransaction() throws IOException {
        synchronized (this) {
            if (currentTransaction != null)
                throw new RuntimeException("Currently only one transaction at a time is supported");
            currentTransaction = new Transaction();
            return currentTransaction;
        }
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
