/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.chunkstore;

import org.apache.commons.codec.binary.*;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.util.encoders.Base64Encoder;
import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StreamHelper;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.*;


public class RepositoryTest extends RepositoryTestBase {
    final ChunkSplitter splitter = new RabinSplitter();
    final ChunkSplitter nodeSplitter = new RabinSplitter();

    private IChunkAccessor getAccessor(final ChunkStore.Transaction chunkStoreTransaction) {
        return new IChunkAccessor() {
            @Override
            public DataInputStream getChunk(BoxPointer hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(chunkStoreTransaction.getChunk(hash.getBoxHash())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data) throws IOException {
                return chunkStoreTransaction.put(data);
            }

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    private IRepoChunkAccessors getRepoChunkAccessors(final ChunkStore chunkStore) {
        return new IRepoChunkAccessors() {
            @Override
            public ITransaction startTransaction() throws IOException {
                return new RepoAccessorsTransactionBase(chunkStore) {
                    final IChunkAccessor accessor = getAccessor(transaction);
                    @Override
                    public IChunkAccessor getCommitAccessor() {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getTreeAccessor() {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getFileAccessor(String filePath) {
                        return accessor;
                    }
                };
            }
        };
    }

    private ChunkStore createChunkStore(File directory, String name) throws IOException {
        assertTrue(!directory.getName().equals("") && !directory.getName().equals("."));
        cleanUpFiles.add(directory.getName());

        return ChunkStore.create(directory, name);
    }

    private HashValue write(IChunkAccessor accessor, TypedBlob blob) throws IOException, CryptoException {
        ChunkContainer chunkContainer = new ChunkContainer(accessor, nodeSplitter);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.getBoxPointer().getBoxHash();
    }

    private TestCommit writeToRepository(IRepoChunkAccessors.ITransaction accessors, TestDirectory root,
                                         String commitMessage)
            throws IOException, CryptoException {
        BoxPointer tree = writeDir(accessors, root, "");
        CommitBox commitBox = CommitBox.create();
        commitBox.setTree(tree);
        commitBox.setCommitMessage(commitMessage.getBytes());

        TestCommit testCommit = new TestCommit();
        testCommit.message = commitMessage;
        testCommit.directory = root;
        testCommit.boxPointer = new BoxPointer(commitBox.hash(), write(accessors.getCommitAccessor(), commitBox));

        return testCommit;
    }

    private FileBox writeToFileBox(IChunkAccessor accessor, String content) throws IOException, CryptoException {
        FileBox file = FileBox.create(accessor, Repository.defaultNodeSplitter(RabinSplitter.CHUNK_8KB));
        ChunkContainer chunkContainer = file.getDataContainer();
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer, splitter);
        containerOutputStream.write(content.getBytes());
        containerOutputStream.flush();
        return file;
    }

    private BoxPointer writeDir(IRepoChunkAccessors.ITransaction accessors, TestDirectory dir, String path)
            throws IOException,
            CryptoException {
        DirectoryBox directoryBox = DirectoryBox.create();
        // first write child dirs recursively
        for (Map.Entry<String, TestDirectory> entry : dir.dirs.entrySet()) {
            BoxPointer childPointer = writeDir(accessors, entry.getValue(), path + "/" + entry.getKey());
            directoryBox.addDir(entry.getKey(), childPointer);
        }

        for (Map.Entry<String, TestFile> entry : dir.files.entrySet()) {
            TestFile testFile = entry.getValue();
            IChunkAccessor fileAccessor = accessors.getFileAccessor(path + "/" + entry.getKey());
            FileBox file = writeToFileBox(fileAccessor, testFile.content);
            testFile.boxPointer = file.getDataContainer().getBoxPointer();
            directoryBox.addFile(entry.getKey(), testFile.boxPointer);
        }

        dir.boxPointer = new BoxPointer(directoryBox.hash(), write(accessors.getTreeAccessor(), directoryBox));
        return dir.boxPointer;
    }

    public TypedBlob getBlob(IChunkAccessor accessor, BoxPointer hashValue) throws IOException, CryptoException {
        ChunkContainer chunkContainer = new ChunkContainer(accessor, hashValue);
        BlobReader blobReader = new BlobReader(new ChunkContainerInputStream(chunkContainer));
        return blobReader.read(accessor);
    }

    private void verifyCommitInRepository(IRepoChunkAccessors.ITransaction accessors, TestCommit testCommit)
            throws IOException,
            CryptoException {
        TypedBlob loaded = getBlob(accessors.getCommitAccessor(), testCommit.boxPointer);
        assert loaded instanceof CommitBox;
        CommitBox commitBox = (CommitBox)loaded;
        assertEquals(testCommit.message, new String(commitBox.getCommitMessage()));
        assertEquals(testCommit.directory.boxPointer.getBoxHash(), commitBox.getTree().getBoxHash());

        verifyDirInRepository(accessors, testCommit.directory, "");
    }

    private void verifyDirInRepository(IRepoChunkAccessors.ITransaction accessors, TestDirectory testDir, String path)
            throws IOException, CryptoException {
        TypedBlob loaded = getBlob(accessors.getTreeAccessor(), testDir.boxPointer);
        assert loaded instanceof DirectoryBox;
        DirectoryBox directoryBox = (DirectoryBox)loaded;
        assertEquals(testDir.dirs.size() + testDir.files.size(), directoryBox.getEntries().size());

        for (Map.Entry<String, TestDirectory> entry : testDir.dirs.entrySet()) {
            DirectoryBox.Entry dirEntry = directoryBox.getEntry(entry.getKey());
            assertNotNull(dirEntry);
            assertEquals(entry.getValue().boxPointer.getBoxHash(), dirEntry.getDataPointer().getBoxHash());

            verifyDirInRepository(accessors, entry.getValue(), path + "/" + entry.getKey());
        }
        for (Map.Entry<String, TestFile> entry : testDir.files.entrySet()) {
            TestFile testFile = entry.getValue();
            DirectoryBox.Entry dirEntry = directoryBox.getEntry(entry.getKey());
            assertNotNull(dirEntry);
            assertEquals(testFile.boxPointer.getBoxHash(), dirEntry.getDataPointer().getBoxHash());

            verifyFileInRepository(accessors.getFileAccessor(path + "/" + entry.getKey()), testFile);
        }
    }

    private void verifyFileInRepository(IChunkAccessor accessor, TestFile testFile) throws IOException, CryptoException {
        FileBox fileBox = FileBox.read(accessor, testFile.boxPointer);
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getDataContainer());
        assertEquals(testFile.content, new String(StreamHelper.readAll(inputStream)));
    }

    public void testBasics() throws IOException, CryptoException {
        String name = "repoTest";
        File directory = new File("RepoTest");
        cleanUpFiles.add(name);
        directory.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(chunkStore);
        IRepoChunkAccessors.ITransaction transaction = accessors.startTransaction();

        TestFile testFile1 = new TestFile("file1Content");
        TestFile testFile2 = new TestFile("file2Content");
        TestFile testFile3 = new TestFile("file3Content");
        TestFile testFile4 = new TestFile("file4Content");

        TestDirectory sub1 = new TestDirectory();
        sub1.files.put("file3", testFile3);

        TestDirectory sub2 = new TestDirectory();
        sub2.files.put("file4", testFile4);

        TestDirectory root = new TestDirectory();
        root.files.put("file1", testFile1);
        root.files.put("file2", testFile2);

        root.dirs.put("sub1", sub1);
        root.dirs.put("sub2", sub2);

        ChunkStoreBranchLog branchLog = new ChunkStoreBranchLog(new File(name, "branch.log"));
        TestCommit testCommit = writeToRepository(transaction, root, "Commit Message");
        branchLog.add(testCommit.boxPointer.getBoxHash().toHex(), Collections.<HashValue>emptyList());
        transaction.finishTransaction();

        branchLog = new ChunkStoreBranchLog(new File(name, "branch.log"));
        ChunkStoreBranchLog.Entry tip = branchLog.getLatest();
        assertNotNull(tip);
        assertEquals(testCommit.boxPointer.getBoxHash(), HashValue.fromHex(tip.getMessage()));

        transaction = accessors.startTransaction();
        verifyCommitInRepository(transaction, testCommit);
    }

    public void testRepositoryBasics() throws Exception {
        String branch = "repoBranch";
        String name = "repoTreeBuilder";
        File directory = new File("RepoTest");
        cleanUpFiles.add(directory.getName());
        directory.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(chunkStore);
        Repository repository = new Repository(directory, branch, accessors, simpleCommitCallback);

        List<DatabaseStingEntry> content = new ArrayList<>();
        add(repository, content, new DatabaseStingEntry("file1", "file1"));
        add(repository, content, new DatabaseStingEntry("dir1/file2", "file2"));
        add(repository, content, new DatabaseStingEntry("dir1/file3", "file3"));
        add(repository, content, new DatabaseStingEntry("dir2/file4", "file4"));
        add(repository, content, new DatabaseStingEntry("dir1/sub1/file5", "file5"));

        repository.commit();

        containsContent(repository, content);
    }
}
