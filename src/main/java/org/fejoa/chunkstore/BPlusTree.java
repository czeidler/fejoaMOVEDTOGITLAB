/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.RandomAccessFile;


public class BPlusTree extends BaseBPlusTree<Long, Long> {
    public BPlusTree(RandomAccessFile file) {
        super(file, new LongType(), new LongType());
    }
}
