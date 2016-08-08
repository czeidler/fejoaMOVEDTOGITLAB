/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.chunkstore.sync;

import org.fejoa.chunkstore.*;
import org.fejoa.chunkstore.sync.CommonAncestorsFinder;
import org.fejoa.chunkstore.sync.DiffIterator;
import org.fejoa.chunkstore.sync.DirBoxDiffIterator;
import org.fejoa.chunkstore.sync.ThreeWayMerge;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.StorageLib;
import org.fejoa.tests.chunkstore.RepositoryTest;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public class DiffMergeTest extends RepositoryTest {
    MessageDigest messageDigest;

    public DiffMergeTest() throws NoSuchAlgorithmException {
         messageDigest = CryptoHelper.sha256Hash();
    }

    private BoxPointer addFile(DirectoryBox box, String name) {
        BoxPointer fakeFilePointer = new BoxPointer(new HashValue(CryptoHelper.sha256Hash(Crypto.get().generateSalt())),
                new HashValue(CryptoHelper.sha256Hash(Crypto.get().generateSalt())));
        box.addFile(name, fakeFilePointer);
        return fakeFilePointer;
    }

    public void testDiff() {
        DirectoryBox ours = DirectoryBox.create();
        DirectoryBox theirs = DirectoryBox.create();

        BoxPointer file1 = addFile(ours, "test1");
        DirBoxDiffIterator iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        DiffIterator.Change change = iterator.next();
        assertEquals(DiffIterator.Type.REMOVED, change.type);
        assertEquals("test1", change.path);
        assertFalse(iterator.hasNext());
        theirs.addFile("test1", file1);

        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertFalse(iterator.hasNext());

        BoxPointer file2 = addFile(theirs, "test2");
        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        change = iterator.next();
        assertEquals(DiffIterator.Type.ADDED, change.type);
        assertEquals("test2", change.path);
        assertFalse(iterator.hasNext());
        ours.addFile("test2", file2);

        BoxPointer file3 = addFile(ours, "test3");
        theirs.addFile("test3", file3);
        BoxPointer file4 = addFile(ours, "test4");
        theirs.addFile("test4", file4);
        BoxPointer file5 = addFile(ours, "test5");
        theirs.addFile("test5", file5);

        BoxPointer file31 = addFile(ours, "test31");
        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        change = iterator.next();
        assertEquals(DiffIterator.Type.REMOVED, change.type);
        assertEquals("test31", change.path);
        assertFalse(iterator.hasNext());

        theirs.addFile("test31", file31);
        BoxPointer file41 = addFile(theirs, "test41");
        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        change = iterator.next();
        assertEquals(DiffIterator.Type.ADDED, change.type);
        assertEquals("test41", change.path);
        assertFalse(iterator.hasNext());

        addFile(ours, "test41");
        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        change = iterator.next();
        assertEquals(DiffIterator.Type.MODIFIED, change.type);
        assertEquals("test41", change.path);
        assertFalse(iterator.hasNext());
    }

    public void testMerge() throws Exception {
        String branch = "repoBranch";
        String name = "repoTreeBuilder";
        File directory = new File("RepoTest");
        File directory2 = new File("RepoTest2");
        cleanUpFiles.add(directory.getName());
        cleanUpFiles.add(directory2.getName());
        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
        directory.mkdirs();
        directory2.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(chunkStore);
        Repository repository = new Repository(directory, branch, accessors, simpleCommitCallback);
        Repository repository2 = new Repository(directory2, branch, accessors, simpleCommitCallback);

        repository.writeBytes("file1", "file1".getBytes());
        repository.commit();

        repository2.writeBytes("file1", "file1".getBytes());
        repository2.commit();

        repository2.writeBytes("file2", "file2".getBytes());
        repository2.commit();

        List<DatabaseStingEntry> mergedContent = new ArrayList<>();
        mergedContent.add(new DatabaseStingEntry("file1", "file1"));
        mergedContent.add(new DatabaseStingEntry("file2", "file2"));

        IRepoChunkAccessors.ITransaction transaction = accessors.startTransaction();
        IChunkAccessor commitAccessor = transaction.getCommitAccessor();

        // test common ancestor finder
        CommitBox ours = repository.getHeadCommit();
        CommitBox theirs = repository2.getHeadCommit();
        CommonAncestorsFinder.Chains chains = CommonAncestorsFinder.find(commitAccessor, ours, commitAccessor, theirs);
        assertTrue(chains.chains.size() == 1);
        CommonAncestorsFinder.SingleCommitChain chain = chains.chains.get(0);
        assertTrue(chain.commits.size() == 2);
        CommitBox parent = chain.commits.get(chain.commits.size() - 1);
        assertTrue(parent.hash().equals(repository.getHeadCommit().hash()));

        repository.merge(transaction, theirs);
        containsContent(repository, mergedContent);

        repository.writeBytes("file2", "our file 2".getBytes());
        repository.commit();
        repository2.writeBytes("file2", "their file 2".getBytes());
        repository2.commit();

        theirs = repository2.getHeadCommit();
        repository.merge(transaction, theirs);

        mergedContent.clear();
        mergedContent.add(new DatabaseStingEntry("file1", "file1"));
        mergedContent.add(new DatabaseStingEntry("file2", "our file 2"));
        containsContent(repository, mergedContent);
    }
}
