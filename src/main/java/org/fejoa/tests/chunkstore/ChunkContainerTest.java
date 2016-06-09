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
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.StreamHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class ChunkContainerTest extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testAppend() throws IOException {
        cleanUpFiles.add("test.idx");
        cleanUpFiles.add("test.pack");

        final ChunkStore chunkStore = ChunkStore.create("test");

        IBlobAccessor accessor = new IBlobAccessor() {
            @Override
            public DataInputStream getBlob(HashValue hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(chunkStore.getChunk(hash.getBytes())));
            }

            @Override
            public void putBlock(HashValue hash, byte[] data) throws IOException {
                chunkStore.put(hash.getBytes(), data);
            }

            @Override
            public HashValue putBlock(IChunk blob) throws IOException {
                HashValue hash = blob.hash();
                putBlock(hash, hash.getBytes());
                return hash;
            }
        };
        ChunkContainer chunkContainer = new ChunkContainer(accessor);
        chunkContainer.setMaxNodeLength(100);

        chunkContainer.append(new DataChunk("Hello".getBytes()));
        chunkContainer.append(new DataChunk(" World!".getBytes()));
        assertEquals(1, chunkContainer.getNLevels());

        chunkContainer.append(new DataChunk(" Split!".getBytes()));
        assertEquals(2, chunkContainer.getNLevels());

        chunkContainer.append(new DataChunk(" more!".getBytes()));
        chunkContainer.append(new DataChunk(" another Split!".getBytes()));
        assertEquals(3, chunkContainer.getNLevels());

        System.out.println(chunkContainer.getBlobLength());
        System.out.println(chunkContainer.printAll());

        chunkContainer.flush();

        // load
        System.out.println("Load:");
        HashValue root = chunkContainer.hash();

        chunkContainer = new ChunkContainer(accessor, root);
        System.out.println(chunkContainer.printAll());

        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(chunkContainer);
        printStream(inputStream);

        inputStream.seek(2);
        printStream(inputStream);

        // test output stream
        chunkContainer = new ChunkContainer(accessor);
        chunkContainer.setMaxNodeLength(100);
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer);
        containerOutputStream.write("Chunk1".getBytes());
        containerOutputStream.flush();
        containerOutputStream.write("Chunk2".getBytes());
        containerOutputStream.flush();
        System.out.println(chunkContainer.printAll());

        inputStream = new ChunkContainerInputStream(chunkContainer);
        printStream(inputStream);
    }

    private void printStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamHelper.copy(inputStream, outputStream);
        System.out.println(new String(outputStream.toByteArray()));
    }
}
