package org.fejoa.tests.chunkstore;


import junit.framework.TestCase;
import org.fejoa.chunkstore.*;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.StreamHelper;

import java.io.*;
import java.util.*;

public class RepositoryTest  extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private IChunkAccessor getAccessor(final ChunkStore chunkStore) {
        return new IChunkAccessor() {
            ChunkStore.Transaction transaction;

            @Override
            public DataInputStream getChunk(HashValue hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(chunkStore.getChunk(hash.getBytes())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data) throws IOException {
                return transaction.put(data);
            }

            @Override
            public void startTransaction() throws IOException {
                transaction = chunkStore.openTransaction();
            }

            @Override
            public void finishTransaction() throws IOException {
                transaction.commit();
                transaction = null;
            }
        };
    }

    private ChunkStore createChunkStore(File directory, String name) throws IOException {
        assertTrue(!directory.getName().equals("") && !directory.getName().equals("."));
        cleanUpFiles.add(directory.getName());

        return ChunkStore.create(directory, name);
    }

    static class TestFile {
        TestFile(String content) {
            this.content = content;
        }

        String content;
        HashValue boxHash;
    }

    static class TestDirectory {
        Map<String, TestFile> files = new HashMap<>();
        Map<String, TestDirectory> dirs = new HashMap<>();
        HashValue boxHash;
    }

    static class TestCommit {
        String message;
        TestDirectory directory;
        HashValue boxHash;
    }

    private HashValue write(IChunkAccessor accessor, TypedBlob blob) throws IOException {
        ChunkContainer chunkContainer = new ChunkContainer(accessor);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.hash();
    }

    private TestCommit writeToRepositiory(IChunkAccessor accessor, TestDirectory root, String commitMessage)
            throws IOException {
        BoxPointer tree = writeDir(accessor, root);
        CommitBox commitBox = CommitBox.create();
        commitBox.setTree(tree);
        commitBox.setCommitMessage(commitMessage.getBytes());

        TestCommit testCommit = new TestCommit();
        testCommit.message = commitMessage;
        testCommit.directory = root;
        testCommit.boxHash = write(accessor, commitBox);

        return testCommit;
    }

    private FileBox writeToFileBox(IChunkAccessor accessor, String content) throws IOException {
        FileBox file = FileBox.create(accessor);
        ChunkContainer chunkContainer = file.getChunkContainer();
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer);
        containerOutputStream.write(content.getBytes());
        containerOutputStream.flush();
        return file;
    }

    private BoxPointer writeDir(IChunkAccessor accessor, TestDirectory dir) throws IOException {
        DirectoryBox directoryBox = DirectoryBox.create();
        // first write child dirs recursively
        for (Map.Entry<String, TestDirectory> entry : dir.dirs.entrySet()) {
            BoxPointer childPointer = writeDir(accessor, entry.getValue());
            directoryBox.addDir(entry.getKey(), childPointer);
        }

        for (Map.Entry<String, TestFile> entry : dir.files.entrySet()) {
            TestFile testFile = entry.getValue();
            FileBox file = writeToFileBox(accessor, testFile.content);

            testFile.boxHash = write(accessor, file);
            directoryBox.addFile(entry.getKey(), new BoxPointer(file.hash(), testFile.boxHash));
        }

        dir.boxHash = write(accessor, directoryBox);
        return new BoxPointer(directoryBox.hash(), dir.boxHash);
    }

    public TypedBlob getBlob(IChunkAccessor accessor, HashValue hashValue) throws IOException {
        ChunkContainer chunkContainer = new ChunkContainer(accessor, hashValue);
        BlobReader blobReader = new BlobReader(new ChunkContainerInputStream(chunkContainer));
        return blobReader.read(accessor);
    }

    private void verifyCommitInRepository(IChunkAccessor accessor, TestCommit testCommit) throws IOException {
        TypedBlob loaded = getBlob(accessor, testCommit.boxHash);
        assert loaded instanceof CommitBox;
        CommitBox commitBox = (CommitBox)loaded;
        assertEquals(testCommit.message, new String(commitBox.getCommitMessage()));
        assertEquals(testCommit.directory.boxHash, commitBox.getTree().getBoxHash());

        verifyDirInRepository(accessor, testCommit.directory);
    }

    private void verifyDirInRepository(IChunkAccessor accessor, TestDirectory testDir) throws IOException {
        TypedBlob loaded = getBlob(accessor, testDir.boxHash);
        assert loaded instanceof DirectoryBox;
        DirectoryBox directoryBox = (DirectoryBox)loaded;
        assertEquals(testDir.dirs.size() + testDir.files.size(), directoryBox.getEntries().size());

        for (Map.Entry<String, TestDirectory> entry : testDir.dirs.entrySet()) {
            DirectoryBox.Entry dirEntry = directoryBox.getEntry(entry.getKey());
            assertNotNull(dirEntry);
            assertEquals(entry.getValue().boxHash, dirEntry.getBoxPointer().getBoxHash());

            verifyDirInRepository(accessor, entry.getValue());
        }
        for (Map.Entry<String, TestFile> entry : testDir.files.entrySet()) {
            TestFile testFile = entry.getValue();
            DirectoryBox.Entry dirEntry = directoryBox.getEntry(entry.getKey());
            assertNotNull(dirEntry);
            assertEquals(testFile.boxHash, dirEntry.getBoxPointer().getBoxHash());

            verifyFileInRepository(accessor, testFile);
        }
    }

    private void verifyFileInRepository(IChunkAccessor accessor, TestFile testFile) throws IOException {
        TypedBlob loaded = getBlob(accessor, testFile.boxHash);
        assert loaded instanceof FileBox;
        FileBox fileBox = (FileBox)loaded;
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getChunkContainer());
        assertTrue(Arrays.equals(StreamHelper.readAll(inputStream), testFile.content.getBytes()));
    }

    public void testBasics() throws IOException {
        String name = "repoTest";
        File directory = new File("RepoTest");
        directory.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IChunkAccessor accessor = getAccessor(chunkStore);
        accessor.startTransaction();

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
        TestCommit testCommit = writeToRepositiory(accessor, root, "Commit Message");
        branchLog.add(testCommit.boxHash, Collections.<HashValue>emptyList());
        accessor.finishTransaction();

        branchLog = new ChunkStoreBranchLog(new File(name, "branch.log"));
        ChunkStoreBranchLog.Entry tip = branchLog.getLatest();
        assertNotNull(tip);
        assertEquals(testCommit.boxHash, tip.getTip());

        verifyCommitInRepository(accessor, testCommit);
    }

    class DatabaseStingEntry {
        public String path;
        public String content;

        public DatabaseStingEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    private void add(Repository database, List<DatabaseStingEntry> content, DatabaseStingEntry entry)
            throws Exception {
        content.add(entry);
        database.writeBytes(entry.path, entry.content.getBytes());
    }

    private void containsContent(Repository database, List<DatabaseStingEntry> content) throws IOException {
        for (DatabaseStingEntry entry : content) {
            byte bytes[] = database.readBytes(entry.path);
            assertTrue(entry.content.equals(new String(bytes)));
        }
    }

    public void testRepositoryBasics() throws Exception {
        String branch = "repoBranch";
        String name = "repoTreeBuilder";
        File directory = new File("RepoTest");
        directory.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IChunkAccessor accessor = getAccessor(chunkStore);
        Repository repository = new Repository(directory, branch, accessor);

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
