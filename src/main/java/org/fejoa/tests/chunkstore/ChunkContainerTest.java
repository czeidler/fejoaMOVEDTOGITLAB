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
import java.util.Iterator;
import java.util.List;


public class ChunkContainerTest extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();
    CryptoSettings settings = CryptoSettings.getDefault();
    ICryptoInterface cryptoInterface = new BCCryptoInterface();
    SecretKey secretKey;
    final ChunkSplitter splitter = new RabinSplitter();

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

            @Override
            public void releaseChunk(HashValue data) {

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

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    private IChunkAccessor getAccessor(ChunkStore chunkStore) throws IOException, CryptoException {
        return getSimpleAccessor(chunkStore);
    }

    private ChunkContainer prepareContainer(String dirName, String name, ChunkSplitter nodeSplitter)
            throws IOException,
            CryptoException {
        cleanUpFiles.add(dirName);
        File dir = new File(dirName);
        dir.mkdirs();

        final ChunkStore chunkStore = ChunkStore.create(dir, name);

        IChunkAccessor accessor = getAccessor(chunkStore);
        ChunkContainer chunkContainer = new ChunkContainer(accessor);
        chunkContainer.setNodeSplitter(nodeSplitter);

        return chunkContainer;
    }

    private ChunkContainer openContainer(String dirName, String name, BoxPointer pointer, ChunkSplitter nodeSplitter)
            throws IOException, CryptoException {
        final ChunkStore chunkStore = ChunkStore.open(new File(dirName), name);
        IChunkAccessor accessor = getAccessor(chunkStore);
        ChunkContainer chunkContainer = new ChunkContainer(accessor, pointer);
        chunkContainer.setNodeSplitter(nodeSplitter);
        return chunkContainer;
    }

    private String toString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamHelper.copy(inputStream, outputStream);
        return new String(outputStream.toByteArray());
    }

    private void printStream(InputStream inputStream) throws IOException {
        System.out.println(toString(inputStream));
    }

    public void testAppend() throws Exception {
        final String dirName = "testContainerDir";
        final String name = "test";
        final ChunkSplitter chunkSplitter = new FixedBlockSplitter(64);
        ChunkContainer chunkContainer = prepareContainer(dirName, name, chunkSplitter);

        chunkContainer.append(new DataChunk("Hello".getBytes()));
        chunkContainer.append(new DataChunk(" World!".getBytes()));
        assertEquals(1, chunkContainer.getNLevels());

        chunkContainer.append(new DataChunk(" Split!".getBytes()));
        System.out.println(chunkContainer.printAll());
        chunkContainer.flush(false);
        assertEquals(2, chunkContainer.getNLevels());

        chunkContainer.append(new DataChunk(" more!".getBytes()));
        chunkContainer.append(new DataChunk(" another Split!".getBytes()));
        chunkContainer.flush(false);
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

        chunkContainer = openContainer(dirName, name, rootPointer, chunkSplitter);
        System.out.println(chunkContainer.printAll());

        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(chunkContainer);
        printStream(inputStream);

        inputStream.seek(2);
        printStream(inputStream);

        // test output stream
        chunkContainer = prepareContainer(dirName, name, chunkSplitter);
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer, splitter);
        containerOutputStream.write("Chunk1".getBytes());
        containerOutputStream.flush();
        containerOutputStream.write("Chunk2".getBytes());
        containerOutputStream.flush();
        System.out.println(chunkContainer.printAll());

        System.out.println("Load from stream:");
        inputStream = new ChunkContainerInputStream(chunkContainer);
        printStream(inputStream);
    }

    public void testEditing() throws Exception {
        final String dirName = "testContainerDir";
        final String name = "test";
        final ChunkSplitter chunkSplitter = new FixedBlockSplitter(64);
        ChunkContainer chunkContainer = prepareContainer(dirName, name, chunkSplitter);

        chunkContainer.append(new DataChunk("Hello".getBytes()));
        System.out.println(chunkContainer.printAll());
        chunkContainer.append(new DataChunk(" World!".getBytes()));
        chunkContainer.append(new DataChunk("more".getBytes()));
        chunkContainer.insert(new DataChunk("22".getBytes()), 5);
        System.out.println(chunkContainer.printAll());

        chunkContainer.flush(false);
        System.out.println(chunkContainer.printAll());

        chunkContainer.remove(5, 2);
        chunkContainer.remove(5, 7);

        chunkContainer.flush(false);
        System.out.println(chunkContainer.printAll());
    }

    public void testHash() throws Exception {
        final String dirName = "testHashDir";
        final String name = "test";
        final ChunkSplitter dataSplitter = new FixedBlockSplitter(2);
        final ChunkSplitter nodeSplitter = new FixedBlockSplitter(64);
        ChunkContainer chunkContainer = prepareContainer(dirName, name, nodeSplitter);

        chunkContainer.append(new DataChunk("11".getBytes()));
        chunkContainer.append(new DataChunk("22".getBytes()));
        chunkContainer.append(new DataChunk("33".getBytes()));
        chunkContainer.insert(new DataChunk("44".getBytes()), 2);
        System.out.println(chunkContainer.printAll());

        chunkContainer.flush(false);
        System.out.println(chunkContainer.printAll());

        ChunkHash chunkHash = new ChunkHash(dataSplitter, nodeSplitter);
        chunkHash.update("11".getBytes());
        chunkHash.update("44".getBytes());
        chunkHash.update("22".getBytes());
        chunkHash.update("33".getBytes());
        assertTrue(Arrays.equals(chunkContainer.hash().getBytes(), chunkHash.digest()));
    }

    private ChunkSplitter createByteTriggerSplitter(final byte trigger) {
        return new ChunkSplitter() {
            @Override
            protected boolean updateInternal(byte i) {
                if (i == trigger)
                    return true;
                return false;
            }

            @Override
            protected void resetInternal() {

            }

            @Override
            public ChunkSplitter newInstance() {
                return createByteTriggerSplitter(trigger);
            }
        };
    }

    public void testVariableSplitter() throws Exception {
        final String dirName = "testVariableSplitterDir";
        final String name = "test";
        final String dataString = "1|2|3|4|5|6|7|8|9|10|11|12|13|14|15|";
        final ChunkSplitter dataSplitter = createByteTriggerSplitter((byte)'|');
        final ChunkSplitter nodeSplitter = new RabinSplitter(128, 48);
        ChunkContainer chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte c : dataString.getBytes()) {
            outputStream.write(c);
            if (dataSplitter.update(c)) {
                chunkContainer.append(new DataChunk(outputStream.toByteArray()));
                outputStream = new ByteArrayOutputStream();
                dataSplitter.reset();
            }
        }
        chunkContainer.flush(false);
        System.out.println(chunkContainer.printAll());
        printStream(new ChunkContainerInputStream(chunkContainer));
        System.out.println("Insert:");
        chunkContainer.insert(new DataChunk("i4|".getBytes()), 6);
        chunkContainer.flush(false);
        String newString = toString(new ChunkContainerInputStream(chunkContainer));
        assertEquals(newString, "1|2|3|i4|4|5|6|7|8|9|10|11|12|13|14|15|");
        System.out.println(chunkContainer.printAll());

        ChunkHash chunkHash = new ChunkHash(dataSplitter, nodeSplitter);
        chunkHash.update(newString.getBytes());
        assertTrue(Arrays.equals(chunkContainer.hash().getBytes(), chunkHash.digest()));
    }

    public void testSeekOutputStream() throws Exception {
        final String dirName = "testSeekOutputStreamDir";
        final String name = "test";
        final ChunkSplitter dataSplitter = createByteTriggerSplitter((byte)'|');
        final ChunkSplitter nodeSplitter = new RabinSplitter(128, 48);
        ChunkContainer chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        ChunkContainerOutputStream outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|2|3|4|".getBytes());
        outputStream.close();
        assertEquals("1|2|3|4|", toString(new ChunkContainerInputStream(chunkContainer)));
        chunkContainer.flush(false);
        assertEquals("1|2|3|4|", toString(new ChunkContainerInputStream(chunkContainer)));

        // append: "1|2|3|4|" -> "1|2|3|4|5|6|"
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(chunkContainer.getDataLength());
        outputStream.write("5|6|".getBytes());
        outputStream.close();
        assertEquals("1|2|3|4|5|6|", toString(new ChunkContainerInputStream(chunkContainer)));

        // overwrite + append: "1|2|3|4|" -> "1|2|3|i|5|6|"
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|2|3|4|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(6);
        outputStream.write("i|5|6|".getBytes());
        outputStream.close();
        assertEquals("1|2|3|i|5|6|", toString(new ChunkContainerInputStream(chunkContainer)));

        // overwrite + append: "1|2|3|4|" -> "1|2|iii|5|6|"
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|2|3|4|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(4);
        outputStream.write("iii|5|6|".getBytes());
        outputStream.close();
        assertEquals("1|2|iii|5|6|", toString(new ChunkContainerInputStream(chunkContainer)));

        //"1|2|3|4|" -> "1|i|i|4|"
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|2|3|4|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(2);
        outputStream.write("i|i|".getBytes());
        outputStream.close();
        assertEquals("1|i|i|4|", toString(new ChunkContainerInputStream(chunkContainer)));
        // try again but with a shorter overwrite:
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|2|3|4|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(2);
        outputStream.write("i|i".getBytes());
        outputStream.close();
        assertEquals("1|i|i|4|", toString(new ChunkContainerInputStream(chunkContainer)));

        //"1|222|3|" -> "1|i|i|3|"
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|222|3|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(2);
        outputStream.write("i|i|".getBytes());
        outputStream.close();
        assertEquals("1|i|i|3|", toString(new ChunkContainerInputStream(chunkContainer)));
        // try again but with a shorter overwrite:
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|222|3|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(2);
        outputStream.write("i|i".getBytes());
        outputStream.close();
        assertEquals("1|i|i|3|", toString(new ChunkContainerInputStream(chunkContainer)));

        //"1|222|3|" -> "1|i|2|3|"
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|222|3|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(2);
        outputStream.write("i|".getBytes());
        outputStream.close();
        assertEquals("1|i|2|3|", toString(new ChunkContainerInputStream(chunkContainer)));

        //"1|2222|3|" -> "1|2i|2|3|"
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|2222|3|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(3);
        outputStream.write("i|".getBytes());
        outputStream.close();
        assertEquals("1|2i|2|3|", toString(new ChunkContainerInputStream(chunkContainer)));

        //"1|222|3|" -> "1|2i2|3|"
        chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write("1|222|3|".getBytes());
        outputStream.close();
        outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.seek(3);
        outputStream.write("i".getBytes());
        outputStream.close();
        assertEquals("1|2i2|3|", toString(new ChunkContainerInputStream(chunkContainer)));
    }

    public void testSeekOutputStreamEditingLarge() throws Exception {
        int nBytes = 1024 * 1000 * 50;
        byte[] data = new byte[nBytes];
        for (int value = 0; value < data.length; value++) {
            byte random = (byte) (256 * Math.random());
            data[value] = random;
            //data[value] = (byte)value;
        }

        final String dirName = "testSeekOutputStreamEditingLarge";
        final String name = "test";
        final ChunkSplitter dataSplitter = new RabinSplitter(RabinSplitter.CHUNK_8KB, 128);
        final ChunkSplitter nodeSplitter = new RabinSplitter(RabinSplitter.CHUNK_8KB, 128);
        ChunkHash chunkHash = new ChunkHash(dataSplitter, nodeSplitter);
        chunkHash.update(data);
        final HashValue dataHash = new HashValue(chunkHash.digest());

        ChunkContainer chunkContainer = prepareContainer(dirName, name, nodeSplitter);
        ChunkContainerOutputStream outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);
        outputStream.write(data);
        outputStream.close();
        chunkContainer.flush(false);

        // verify
        chunkContainer = openContainer(dirName, name, chunkContainer.getBoxPointer(), nodeSplitter);
        Iterator<ChunkContainer.DataChunkPointer> iter = chunkContainer.getChunkIterator(0);
        chunkHash = new ChunkHash(dataSplitter, nodeSplitter);
        while (iter.hasNext()) {
            ChunkContainer.DataChunkPointer pointer = iter.next();
            chunkHash.update(pointer.getDataChunk().getData());
        }

        assertTrue(Arrays.equals(dataHash.getBytes(), chunkHash.digest()));
    }
}
