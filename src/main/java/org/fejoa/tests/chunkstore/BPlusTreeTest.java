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
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.*;


public class BPlusTreeTest extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    class TestTree {
        final BPlusTree tree;
        final Map<String, Long> entries = new HashMap<>();

        public TestTree(BPlusTree tree) {
            this.tree = tree;
        }

        public void put(String key, Long value) throws IOException {
            tree.put(HashValue.fromHex(key), value);
            entries.put(key, value);
        }

        public boolean remove(String key) throws IOException {
            boolean result = tree.remove(key);
            if (!result)
                return result;
            entries.put(key, null);
            return result;
        }

        public void validate() throws IOException {
            for (Map.Entry<String, Long> entry : entries.entrySet())
                assertEquals(entry.getValue(), tree.get(entry.getKey()));

            System.out.println("Deleted tiles: " + tree.countDeletedTiles());
        }

        public void print() throws IOException {
            tree.print();
        }
    }
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private int tileSize(int nKeys, int hashSize, BPlusTree tree) {
        int indexPointerSize = tree.getIndexType().size();

        return 2 * indexPointerSize + nKeys * (indexPointerSize + hashSize);
    }

    public void testSimple() throws Exception {
        String fileName = "test.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0005", 5l);
        tree.put("0003", 3l);
        tree.print();
        tree.put("0004", 4l);
        tree.print();
        tree.put("0006", 6l);
        tree.print();
        tree.put("0009", 90l);
        tree.print();
        tree.put("0002", 2l);
        tree.print();
        tree.put("0001", 1l);
        tree.print();

        tree.validate();
    }

    public void testRemove() throws Exception {
        String fileName = "remove.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0005", 5l);
        tree.put("0003", 3l);
        tree.put("0002", 2l);
        tree.print();

        assertFalse(tree.remove("0008"));
        tree.remove("0003");

        tree.validate();
        tree.print();
    }

    public void testRemove2() throws Exception {
        String fileName = "remove2.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0005", 5l);
        tree.put("0003", 3l);
        tree.put("0002", 2l);

        tree.remove("0005");

        tree.validate();
        tree.print();
    }

    public void testRemove3() throws Exception {
        String fileName = "remove3.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0001", 1l);
        tree.put("0002", 2l);
        tree.put("0003", 3l);
        tree.put("0004", 4l);
        tree.put("0005", 5l);
        tree.put("0006", 6l);
        tree.put("0007", 7l);

        tree.remove("0003");
        tree.validate();

        tree.print();
    }

    public void testRemove4() throws Exception {
        String fileName = "remove4.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0001", 1l);
        tree.put("0002", 2l);
        tree.put("0003", 3l);
        tree.put("0004", 4l);
        tree.put("0005", 5l);
        tree.put("0006", 6l);
        tree.put("0007", 7l);

        tree.remove("0007");
        tree.validate();

        tree.print();
    }

    public void testRemove5() throws Exception {
        String fileName = "remove5.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0001", 1l);
        tree.put("0002", 2l);
        tree.put("0003", 3l);
        tree.put("0004", 4l);
        tree.put("0005", 5l);
        tree.put("0006", 6l);
        tree.put("0007", 7l);

        tree.print();
        tree.remove("0001");
        tree.validate();

        tree.print();
    }

    public void testRemove6() throws Exception {
        String fileName = "remove6.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0001", 1l);
        tree.put("0002", 2l);
        tree.put("0003", 3l);
        tree.put("0004", 4l);
        tree.put("0005", 5l);
        tree.put("0006", 6l);
        tree.put("0007", 7l);

        tree.remove("0004");
        tree.validate();

        tree.print();
    }

    public void testRemove7() throws Exception {
        String fileName = "remove7.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0001", 1l);

        assertTrue(tree.remove("0001"));
        tree.validate();
        tree.print();
        tree.put("0002", 2l);
        tree.print();
        tree.validate();
    }

    public void testRemove8() throws Exception {
        String fileName = "remove8.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0001", 1l);
        tree.put("0002", 2l);
        tree.put("0003", 3l);
        tree.put("0004", 4l);
        tree.put("0005", 5l);
        tree.put("0006", 6l);
        tree.put("0007", 7l);

        tree.print();
        tree.remove("0005");
        tree.validate();
        tree.print();
    }

    public void testRemove9() throws Exception {
        String fileName = "remove9.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create((short)2, tileSize(2, 2, bTree));
        TestTree tree = new TestTree(bTree);
        tree.put("0001", 1l);
        tree.put("0002", 2l);
        tree.put("0003", 3l);
        tree.put("0004", 4l);
        tree.put("0005", 5l);
        tree.put("0007", 7l);
        tree.put("0008", 8l);
        tree.put("0006", 6l);

        tree.remove("0008");
        tree.print();
        tree.remove("0007");
        tree.validate();
        tree.print();
    }


    private void add(TestTree tree, Random generator, int items, List<String> added) throws IOException {
        for (int i = 0; i < items; i++) {
            Long value = (long) (Long.MAX_VALUE * generator.nextDouble());
            String hash = CryptoHelper.sha256HashHex(value.toString());
            tree.put(hash, value);
            if (added != null)
                added.add(hash);
        }
    }

    private void remove(TestTree tree, Random generator, int items, List<String> toRemove) throws IOException {
        for (int i = 0; i < items; i++) {
            int value = (int) (toRemove.size() * generator.nextDouble());
            String hash = toRemove.remove(value);
            assertTrue(tree.remove(hash));
        }
    }

    public void testAddingLarge() throws IOException {
        String fileName = "addLarge.idx";
        cleanUpFiles.add(fileName);

        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        BPlusTree bTree = new BPlusTree(file);
        bTree.create(32, 1024);
        TestTree tree = new TestTree(bTree);
        Random generator = new Random(1);
        List<String> added = new ArrayList<>();
        add(tree, generator, 5000, added);
        tree.validate();

        remove(tree, generator, 2000, added);
        tree.validate();

        add(tree, generator, 1000, added);
        tree.validate();
    }

    private BigInteger hash(BigInteger number) {
        int n = 50;
        BigInteger p = new BigInteger("103");
        return number.pow(n).mod(p);
    }

    private BigInteger hash2(BigInteger n1, BigInteger n2) {
        BigInteger p = new BigInteger("103");
        return n1.modPow(n2, p);
    }

    private BigInteger hom(BigInteger n1) {
        BigInteger e = new BigInteger("40");
        BigInteger p = new BigInteger("103");

        return n1.modPow(e, p);
    }

    public void testTemp() {
        BigInteger salt = new BigInteger("32");
        BigInteger c0 = new BigInteger("78");
        BigInteger c1 = new BigInteger("58");
        BigInteger c2 = new BigInteger("777");
        BigInteger c3 = new BigInteger("72");
        BigInteger c4 = new BigInteger("12");


        BigInteger total = hash(c0.multiply(c3).multiply(c1).multiply(c2));
        assertEquals(total, hash(c2.multiply(c1).multiply(c0).multiply(c3)));

        total = hash2(hash2(salt, c0), c1);
        assertEquals(total, hash2(hash2(salt, c1), c0));
        assertEquals(total, hash2(salt, c1.multiply(c0)));

        assertEquals(hom(c0).multiply(hom(c1)).multiply(hom(c2)).mod(new BigInteger("103")),
                hom(c0.multiply(c1).multiply(c2)));

        total = hom(hom(c0.multiply(c1).multiply(c2).multiply(c3).multiply(c4).multiply(salt)));
        BigInteger accessRequest = c1.multiply(c3);
        BigInteger rest = hom(c0.multiply(c2).multiply(c4).multiply(salt));
        assertEquals(total, hom(hom(accessRequest)).multiply(hom(rest)).mod(new BigInteger("103")));
    }
}
