/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.chunkstore;


import junit.framework.TestCase;
import org.fejoa.chunkstore.ExtensibleHashMap;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class ExtensibleHashMapTest extends TestCase {
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
        ExtensibleHashMap hashMap = new ExtensibleHashMap();
        hashMap.create(file, 0, (short)4, (short)1);
        hashMap.put("00000000", 0l);
        hashMap.put("00010000", 1l);
        assertEquals(2, hashMap.directorySize());
        hashMap.put("00020000", 2l);
        assertEquals(4, hashMap.directorySize());

        assertEquals(0l, hashMap.get("00000000").longValue());
        assertEquals(1l, hashMap.get("00010000").longValue());
        assertEquals(2l, hashMap.get("00020000").longValue());
        assertEquals(null, hashMap.get("00030000"));
        assertEquals(null, hashMap.get("00040000"));
        assertEquals(null, hashMap.get("00050000"));
        assertEquals(null, hashMap.get("00060000"));
        assertEquals(null, hashMap.get("00070000"));
        assertEquals(null, hashMap.get("00080000"));
    }

    public void testCreateOpen() throws Exception {
        String fileName = "test.idx";
        cleanUpFiles.add(fileName);

        //create
        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        ExtensibleHashMap hashMap = new ExtensibleHashMap();
        hashMap.create(file, 0, (short)4, (short)1);
        hashMap.put("00000000", 0l);
        hashMap.put("00010000", 1l);
        assertEquals(2, hashMap.directorySize());
        hashMap.put("00020000", 2l);
        assertEquals(4, hashMap.directorySize());
        file.close();

        // open
        file = new RandomAccessFile(fileName, "rw");
        hashMap = new ExtensibleHashMap();
        hashMap.open(file, 0);
        assertEquals(0l, hashMap.get("00000000").longValue());
        assertEquals(1l, hashMap.get("00010000").longValue());
        assertEquals(2l, hashMap.get("00020000").longValue());
        assertEquals(null, hashMap.get("00030000"));
        assertEquals(null, hashMap.get("00040000"));
        assertEquals(null, hashMap.get("00050000"));
        assertEquals(null, hashMap.get("00060000"));
        assertEquals(null, hashMap.get("00070000"));
        assertEquals(null, hashMap.get("00080000"));
    }

    public void testMultiSplit() throws Exception {
        String fileName = "testMultiSplit.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        ExtensibleHashMap hashMap = new ExtensibleHashMap();
        hashMap.create(file, 0, (short)4, (short)1);
        hashMap.put("00020000", 2l);
        hashMap.put("00030000", 3l);
        hashMap.put("00060000", 6l);
        assertEquals(8, hashMap.directorySize());
        hashMap.print();
    }

    public void testRedistribute() throws Exception {
        String fileName = "testRedistribute.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        ExtensibleHashMap hashMap = new ExtensibleHashMap();
        hashMap.create(file, 0, (short)4, (short)1);
        hashMap.put("00010000", 1l);
        hashMap.put("00020000", 2l);
        hashMap.put("00000000", 0l);
        hashMap.put("00030000", 3l);

        hashMap.print();
    }

    public void testError0() throws Exception {
        String fileName = "testError0.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        ExtensibleHashMap hashMap = new ExtensibleHashMap();
        hashMap.create(file, 0, (short)4, (short)1);
        hashMap.put("00010000", 1l);
        hashMap.put("00020000", 2l);
        hashMap.put("00000000", 0l);
        hashMap.put("00040000", 4l);

        hashMap.print();
    }

    public void testDuplicate() throws Exception {
        String fileName = "testDuplicate.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        ExtensibleHashMap hashMap = new ExtensibleHashMap();
        hashMap.create(file, 0, (short)4, (short)1);
        hashMap.put("00010000", 1l);
        hashMap.put("00020000", 2l);
        hashMap.put("00000000", 0l);
        hashMap.print();
    }
}
