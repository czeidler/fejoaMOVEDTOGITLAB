/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;


public class ChunkStore {
    final private BPlusTree tree;
    final private PackFile packFile;

    protected ChunkStore(String name) throws FileNotFoundException {
        this.tree = new BPlusTree(new RandomAccessFile(name + "idx", "rw"));
        this.packFile = new PackFile(new RandomAccessFile(name + "pack", "rw"));
    }

    static public ChunkStore create(String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(name);
        chunkStore.tree.create(hashSize(), 1024);
        chunkStore.packFile.create(hashSize());
        return chunkStore;
    }

    static public ChunkStore open(String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(name);
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

    public void put(String hash, byte[] data) throws IOException {
        put(CryptoHelper.fromHex(hash), data);
    }

    public void put(byte[] hash, byte[] data) throws IOException {
        if (hash.length != hashSize())
            throw new IOException("Hash size miss match");
        long position = packFile.put(hash, data);
        tree.put(hash, position);
    }

    static private int hashSize() {
        return 32;
    }

}
