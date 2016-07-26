/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.chunkstore;

import junit.framework.TestCase;
import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoHelper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.fejoa.chunkstore.RabinSplitter.*;


public class ChunkHashTest extends TestCase {
    public void testSimple() throws NoSuchAlgorithmException {
        MessageDigest messageDigest = CryptoHelper.sha256Hash();
        ChunkHash chunkHash = new ChunkHash(new FixedBlockSplitter(2), new FixedBlockSplitter(64));

        byte[] block1 = "11".getBytes();
        messageDigest.update(block1);
        byte[] hashBlock1 = messageDigest.digest();
        chunkHash.update(block1);
        assertTrue(Arrays.equals(hashBlock1, chunkHash.digest()));

        // second layer
        byte[] block2 = "22".getBytes();
        messageDigest.reset();
        messageDigest.update(block2);
        byte[] hashBlock2 = messageDigest.digest();
        messageDigest.reset();
        messageDigest.update(hashBlock1);
        messageDigest.update(hashBlock2);
        byte[] combinedHashLayer2_0 = messageDigest.digest();

        chunkHash.reset();
        chunkHash.update(block1);
        chunkHash.update(block2);
        assertTrue(Arrays.equals(combinedHashLayer2_0, chunkHash.digest()));

        // third layer
        byte[] block3 = "33".getBytes();
        messageDigest.reset();
        messageDigest.update(block3);
        byte[] hashBlock3 = messageDigest.digest();
        messageDigest.reset();
        messageDigest.update(hashBlock3);
        byte[] combinedHashLayer2_1 = messageDigest.digest();
        messageDigest.reset();
        messageDigest.update(combinedHashLayer2_0);
        messageDigest.update(combinedHashLayer2_1);
        byte[] combinedHashLayer3_0 = messageDigest.digest();

        chunkHash.reset();
        chunkHash.update(block1);
        chunkHash.update(block2);
        chunkHash.update(block3);
        assertTrue(Arrays.equals(combinedHashLayer3_0, chunkHash.digest()));

        // fill third layer
        byte[] block4 = "44".getBytes();
        messageDigest.reset();
        messageDigest.update(block4);
        byte[] hashBlock4 = messageDigest.digest();
        messageDigest.reset();
        messageDigest.update(hashBlock3);
        messageDigest.update(hashBlock4);
        byte[] combinedHashLayer2_2 = messageDigest.digest();
        messageDigest.reset();
        messageDigest.update(combinedHashLayer2_0);
        messageDigest.update(combinedHashLayer2_2);
        byte[] combinedHashLayer3_1 = messageDigest.digest();

        chunkHash.reset();
        chunkHash.update(block1);
        chunkHash.update(block2);
        chunkHash.update(block3);
        chunkHash.update(block4);
        assertTrue(Arrays.equals(combinedHashLayer3_1, chunkHash.digest()));
    }

    public void testSimpleBenchmark() throws NoSuchAlgorithmException {
        Integer[] fileSizes = {
                1024 * 256,
                1024 * 512,
                1024 * 1024 * 1,
                1024 * 1024 * 2,
                1024 * 1024 * 4,
                1024 * 1024 * 8,
                1024 * 1024 * 16,
                1024 * 1024 * 32,
                1024 * 1024 * 64,
                //1024 * 1024 * 128,
                //1024 * 1024 * 256,
                //1024 * 1024 * 512,
                //1024 * 1024 * 1024,
        };

        class ChunkSizeTarget {
            int size;
            int minSize;

            public ChunkSizeTarget(int size, int minSize) {
                this.size = size;
                this.minSize = minSize;
            }

            @Override
            public String toString() {
                return (size / 1024) + "Kb_min" + (minSize / 1024) + "Kb";
            }
        }

        final List<ChunkSizeTarget> chunkSizeTargetList = new ArrayList<>();
        chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_8KB, 2 * 1024));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_16KB, 2 * 1024));
        chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_64KB, 2 * 1024));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_32KB, 2 * 1024));
        chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_8KB, 4 * 1024));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_16KB, 4 * 1024));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_32KB, 4 * 1024));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_64KB, "64Kb"));
        //chunkSizeTargetList.add(new ChunkSizeTarget(CHUNK_128KB, "128Kb"));

        float kFactor = (32f) / (32 * 3 + 8);
        int maxSize = 1024 * 512;

        class Result {
            int fileSize;
            List<Long> sha265Times = new ArrayList<>();
            List<List<Long>> chunkHashTimes = new ArrayList<>(new ArrayList<List<Long>>());

            Result() {
                for (int i = 0; i < chunkSizeTargetList.size(); i++)
                    chunkHashTimes.add(new ArrayList<Long>());
            }
        }

        List<Result> results = new ArrayList<>();
        int nIgnore = 3;
        int numberIt = nIgnore + 10;
        for (Integer size : fileSizes) {
            System.out.println("File size: " + size);
            MessageDigest messageDigest = CryptoHelper.sha256Hash();

            byte[] data = new byte[size];
            for (int value = 0; value < data.length; value++) {
                byte random = (byte) (256 * Math.random());
                data[value] = random;
            }

            Result result = new Result();
            result.fileSize = size;
            results.add(result);

            for (int i = 0; i < numberIt; i++) {
                long startTime = System.currentTimeMillis();
                //messageDigest.update(data);
                for (byte b : data)
                    messageDigest.update(b);

                long time = System.currentTimeMillis() - startTime;
                result.sha265Times.add(time);
                System.out.println("Time sha256: " + time + " " + new HashValue(messageDigest.digest()));
                messageDigest.reset();
            }

            for (int sizeIndex = 0; sizeIndex < chunkSizeTargetList.size(); sizeIndex++) {
                ChunkSizeTarget chunkSizeTarget = chunkSizeTargetList.get(sizeIndex);
                Integer chunkSize = chunkSizeTarget.size;
                System.out.println("Target Chunk Size: " + chunkSize);
                byte[] prevHash = null;
                ChunkHash chunkHash = new ChunkHash(new RabinSplitter(chunkSize, chunkSizeTarget.minSize, maxSize),
                        new RabinSplitter((int) (kFactor * chunkSize), (int) (kFactor * chunkSizeTarget.minSize),
                                (int) (kFactor * maxSize)));
                //ChunkHash chunkHash = new ChunkHash(new FixedBlockSplitter(1024 * 8), new FixedBlockSplitter(1024 * 8));

                List<Long> chunkHashTimes = result.chunkHashTimes.get(sizeIndex);
                for (int i = 0; i < numberIt; i++) {
                    long startTime = System.currentTimeMillis();
                    for (byte b : data)
                        chunkHash.update(b);

                    byte[] hash = chunkHash.digest();
                    long time = System.currentTimeMillis() - startTime;
                    chunkHashTimes.add(time);
                    System.out.println("Time cached hash: " + time + " " + new HashValue(hash));

                    if (prevHash != null)
                        assert Arrays.equals(prevHash, hash);
                    prevHash = hash;
                    chunkHash.reset();
                }
            }

        }

        // print results
        System.out.print("FileSize, SHA256Time");
        for (ChunkSizeTarget chunkSizeTarget : chunkSizeTargetList)
            System.out.print(", ChunkSize" + chunkSizeTarget.toString() + "Time");
        System.out.println();
        for (Result result : results) {
            for (int i = nIgnore; i < numberIt; i++) {
                System.out.print(result.fileSize + ", " + result.sha265Times.get(i));
                for (int a = 0; a < chunkSizeTargetList.size(); a++) {
                    List<Long> sizeResults = result.chunkHashTimes.get(a);
                    System.out.print(", " + sizeResults.get(i));
                }
                System.out.println();
            }
        }
    }
}
