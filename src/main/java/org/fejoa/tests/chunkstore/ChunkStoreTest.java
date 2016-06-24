/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.chunkstore;

import junit.framework.TestCase;
import org.fejoa.chunkstore.ChunkStore;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChunkStoreTest  extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testSimple() throws Exception {
        String dirName = "testDir";
        cleanUpFiles.add(dirName);

        ChunkStore chunkStore = ChunkStore.create(new File("dirName"), "test");
        byte[] data1 = "Hello".getBytes();
        byte[] data2 = "Test Data".getBytes();
        ChunkStore.Transaction transaction = chunkStore.openTransaction("testBranch");
        transaction.put(new HashValue(CryptoHelper.sha1Hash(data1)), data1);
        transaction.put(new HashValue(CryptoHelper.sha1Hash(data2)), data2);
        transaction.commit(new HashValue(CryptoHelper.fromHex("FF")));

        assertEquals(new String(data1), new String(chunkStore.getChunk(CryptoHelper.sha1Hash(data1))));
        assertEquals(new String(data2), new String(chunkStore.getChunk(CryptoHelper.sha1Hash(data2))));
    }
}
