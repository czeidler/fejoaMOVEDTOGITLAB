/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.chunkstore;

import junit.framework.TestCase;
import org.fejoa.chunkstore.BPlusTree;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;


public class BPlusTreeTest extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testSimple() throws Exception {
        String fileName = "test.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree tree = new BPlusTree(file);
        tree.create((short)2);
        tree.printHeader();
        tree.put("0005", 5l);
        tree.put("0003", 3l);
        tree.print(true);
        tree.put("0004", 4l);
        tree.print(true);
        tree.put("0006", 6l);
        tree.print(true);
        tree.put("0009", 90l);
        tree.print(true);
        tree.put("0002", 2l);
        tree.print(true);
        tree.put("0001", 1l);
        tree.print(true);

        assertEquals((Long)2l, tree.get("0002"));
        assertEquals((Long)90l, tree.get("0009"));
        assertEquals(null, tree.get("0007"));
    }
}
