package org.fejoa.tests.chunkstore;


import junit.framework.TestCase;
import org.fejoa.chunkstore.*;
import org.fejoa.library.database.DatabaseDir;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.StreamHelper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class RepositoryTest  extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private IBlobAccessor getAccessor(final ChunkStore chunkStore) {
        return new IBlobAccessor() {
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
    }

    private ChunkStore createChunkStore(String name) throws IOException {
        cleanUpFiles.add(name + ".idx");
        cleanUpFiles.add(name + ".pack");

        return ChunkStore.create(name);
    }

    private ChunkContainer openContainer(String name, HashValue hash) throws IOException {
        final ChunkStore chunkStore = ChunkStore.open(name);
        return new ChunkContainer(getAccessor(chunkStore), hash);
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

    private TestCommit writeToRepositiory(Repository repository, TestDirectory root, String commitMessage)
            throws IOException {
        BoxPointer tree = writeDir(repository, root);
        CommitBox commitBox = CommitBox.create();
        commitBox.setTree(tree);
        commitBox.setCommitMessage(commitMessage.getBytes());

        TestCommit testCommit = new TestCommit();
        testCommit.message = commitMessage;
        testCommit.directory = root;
        testCommit.boxHash = repository.put(commitBox);

        return testCommit;
    }

    private BoxPointer writeDir(Repository repository, TestDirectory dir) throws IOException {
        DirectoryBox directoryBox = DirectoryBox.create();
        // first write child dirs recursively
        for (Map.Entry<String, TestDirectory> entry : dir.dirs.entrySet()) {
            BoxPointer childPointer = writeDir(repository, entry.getValue());
            directoryBox.addEntry(entry.getKey(), childPointer);
        }

        for (Map.Entry<String, TestFile> entry : dir.files.entrySet()) {
            TestFile testFile = entry.getValue();
            FileBox file = FileBox.create(repository.getBlobAccessor());
            ChunkContainer chunkContainer = file.getChunkContainer();
            ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer);
            containerOutputStream.write(testFile.content.getBytes());
            containerOutputStream.flush();

            testFile.boxHash = repository.put(file);
            directoryBox.addEntry(entry.getKey(), new BoxPointer(file.hash(), testFile.boxHash));
        }

        dir.boxHash = repository.put(directoryBox);
        return new BoxPointer(directoryBox.hash(), dir.boxHash);
    }

    private void verifyCommitInRepository(Repository repository, TestCommit testCommit) throws IOException {
        TypedBlob loaded = repository.get(testCommit.boxHash);
        assert loaded instanceof CommitBox;
        CommitBox commitBox = (CommitBox)loaded;
        assertEquals(testCommit.message, new String(commitBox.getCommitMessage()));
        assertEquals(testCommit.directory.boxHash, commitBox.getTree().getBoxHash());

        verifyDirInRepository(repository, testCommit.directory);
    }

    private void verifyDirInRepository(Repository repository, TestDirectory testDir) throws IOException {
        TypedBlob loaded = repository.get(testDir.boxHash);
        assert loaded instanceof DirectoryBox;
        DirectoryBox directoryBox = (DirectoryBox)loaded;
        assertEquals(testDir.dirs.size() + testDir.files.size(), directoryBox.getEntries().size());

        for (Map.Entry<String, TestDirectory> entry : testDir.dirs.entrySet()) {
            DirectoryBox.Entry dirEntry = directoryBox.getEntry(entry.getKey());
            assertNotNull(dirEntry);
            assertEquals(entry.getValue().boxHash, dirEntry.getEntryHash().getBoxHash());

            verifyDirInRepository(repository, entry.getValue());
        }
        for (Map.Entry<String, TestFile> entry : testDir.files.entrySet()) {
            TestFile testFile = entry.getValue();
            DirectoryBox.Entry dirEntry = directoryBox.getEntry(entry.getKey());
            assertNotNull(dirEntry);
            assertEquals(testFile.boxHash, dirEntry.getEntryHash().getBoxHash());

            verifyFileInRepository(repository, testFile);
        }
    }

    private void verifyFileInRepository(Repository repository, TestFile testFile) throws IOException {
        TypedBlob loaded = repository.get(testFile.boxHash);
        assert loaded instanceof FileBox;
        FileBox fileBox = (FileBox)loaded;
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getChunkContainer());
        assertTrue(Arrays.equals(StreamHelper.readAll(inputStream), testFile.content.getBytes()));
    }

    public void testBasics() throws IOException {
        String name = "repoTest";
        ChunkStore chunkStore = createChunkStore(name);
        IBlobAccessor accessor = getAccessor(chunkStore);
        Repository repository = new Repository(accessor);

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


        TestCommit testCommit = writeToRepositiory(repository, root, "Commit Message");

        verifyCommitInRepository(repository, testCommit);
    }
}
