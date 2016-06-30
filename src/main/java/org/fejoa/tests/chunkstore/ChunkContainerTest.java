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
import org.fejoa.library.crypto.*;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.StreamHelper;

import javax.crypto.SecretKey;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ChunkContainerTest extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();
    CryptoSettings settings = CryptoSettings.getDefault();
    ICryptoInterface cryptoInterface = new BCCryptoInterface();
    SecretKey secretKey;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        secretKey = cryptoInterface.generateSymmetricKey(settings.symmetric);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private IChunkAccessor getSimpleAccessor(final ChunkStore chunkStore) throws IOException {
        return new IChunkAccessor() {
            ChunkStore.Transaction transaction = chunkStore.openTransaction();

            @Override
            public DataInputStream getChunk(BoxPointer hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(chunkStore.getChunk(hash.getBoxHash().getBytes())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data) throws IOException {
                return transaction.put(data);
            }
        };
    }

    private IChunkAccessor getEncAccessor(final ChunkStore chunkStore) throws CryptoException, IOException {
        return new IChunkAccessor() {
            ChunkStore.Transaction transaction = chunkStore.openTransaction();
            
            private byte[] getIv(byte[] hashValue) {
                return Arrays.copyOfRange(hashValue, 0, settings.symmetric.ivSize);
            }

            @Override
            public DataInputStream getChunk(BoxPointer hash) throws IOException, CryptoException {
                byte[] iv = getIv(hash.getDataHash().getBytes());
                return new DataInputStream(cryptoInterface.decryptSymmetric(new ByteArrayInputStream(
                            chunkStore.getChunk(hash.getBoxHash().getBytes())),
                        secretKey, iv,settings.symmetric));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data) throws IOException, CryptoException {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                OutputStream cryptoStream = cryptoInterface.encryptSymmetric(outputStream, secretKey,
                        getIv(CryptoHelper.sha256Hash(data)), settings.symmetric);
                cryptoStream.write(data);
                return transaction.put(outputStream.toByteArray());
            }
        };
    }

    private IChunkAccessor getAccessor(ChunkStore chunkStore) throws IOException, CryptoException {
        return getEncAccessor(chunkStore);
    }

    private ChunkContainer prepareContainer(String dirName, String name, int maxNodeLength) throws IOException,
            CryptoException {
        cleanUpFiles.add(dirName);
        File dir = new File(dirName);
        dir.mkdirs();

        final ChunkStore chunkStore = ChunkStore.create(dir, name);

        IChunkAccessor accessor = getAccessor(chunkStore);
        ChunkContainer chunkContainer = new ChunkContainer(accessor);
        chunkContainer.setMaxNodeLength(maxNodeLength);

        return chunkContainer;
    }

    private ChunkContainer openContainer(String dirName, String name, BoxPointer pointer) throws IOException,
            CryptoException {
        final ChunkStore chunkStore = ChunkStore.open(new File(dirName), name);
        IChunkAccessor accessor = getAccessor(chunkStore);
        return new ChunkContainer(accessor, pointer);
    }

    private void printStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamHelper.copy(inputStream, outputStream);
        System.out.println(new String(outputStream.toByteArray()));
    }

    public void testAppend() throws Exception {
        final String dirName = "testContainerDir";
        final String name = "test";
        final int maxNodeLength = 170;
        ChunkContainer chunkContainer = prepareContainer(dirName, name, maxNodeLength);

        chunkContainer.append(new DataChunk("Hello".getBytes()));
        chunkContainer.append(new DataChunk(" World!".getBytes()));
        assertEquals(1, chunkContainer.getNLevels());

        chunkContainer.append(new DataChunk(" Split!".getBytes()));
        assertEquals(2, chunkContainer.getNLevels());

        chunkContainer.append(new DataChunk(" more!".getBytes()));
        chunkContainer.append(new DataChunk(" another Split!".getBytes()));
        assertEquals(3, chunkContainer.getNLevels());

        chunkContainer.append(new DataChunk(" and more!".getBytes()));

        System.out.println(chunkContainer.getBlobLength());
        System.out.println(chunkContainer.printAll());

        chunkContainer.flush(false);

        System.out.println("After flush:");
        System.out.println(chunkContainer.printAll());

        // load
        System.out.println("Load:");
        BoxPointer rootPointer = chunkContainer.getBoxPointer();

        chunkContainer = openContainer(dirName, name, rootPointer);
        System.out.println(chunkContainer.printAll());

        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(chunkContainer);
        printStream(inputStream);

        inputStream.seek(2);
        printStream(inputStream);

        // test output stream
        chunkContainer = prepareContainer(dirName, name, maxNodeLength);
        chunkContainer.setMaxNodeLength(maxNodeLength);
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer);
        containerOutputStream.write("Chunk1".getBytes());
        containerOutputStream.flush();
        containerOutputStream.write("Chunk2".getBytes());
        containerOutputStream.flush();
        System.out.println(chunkContainer.printAll());

        System.out.println("Load from stream:");
        inputStream = new ChunkContainerInputStream(chunkContainer);
        printStream(inputStream);
    }
}
