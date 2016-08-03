/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


public class FixedBlockSplitter extends ChunkSplitter {
    final private int blockSize;
    private int nBytesInBlock;

    public FixedBlockSplitter(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    @Override
    public boolean updateInternal(byte i) {
        nBytesInBlock++;
        if (nBytesInBlock >= blockSize)
            return true;

        return false;
    }

    @Override
    public void resetInternal() {
        nBytesInBlock = 0;
    }

    @Override
    public ChunkSplitter newInstance() {
        return new FixedBlockSplitter(blockSize);
    }
}
