/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.chunkstore.sync.ChunkFetcher;
import org.fejoa.chunkstore.sync.CommonAncestorsFinder;
import org.fejoa.chunkstore.sync.ThreeWayMerge;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StreamHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class Repository {
    final private File dir;
    final private String branch;
    final private ChunkStoreBranchLog log;
    private CommitBox headCommit;
    final private ICommitCallback commitCallback;
    final private IRepoChunkAccessors accessors;
    private LogRepoTransaction transaction;
    private TreeAccessor treeAccessor;
    final private ChunkSplitter chunkSplitter = new RabinSplitter();

    public interface ICommitCallback {
        String commitPointerToLog(BoxPointer commitPointer);
        BoxPointer commitPointerFromLog(String logEntry);
        byte[] createCommitMessage(String message, BoxPointer rootTree, Collection<BoxPointer> parents);
     }

    public Repository(File dir, String branch, IRepoChunkAccessors chunkAccessors, ICommitCallback commitCallback)
            throws IOException, CryptoException {
        this.dir = dir;
        this.branch = branch;
        this.accessors = chunkAccessors;
        this.transaction = new LogRepoTransaction(accessors.startTransaction());
        this.log = new ChunkStoreBranchLog(new File(getBranchDir(), branch));
        this.commitCallback = commitCallback;

        BoxPointer headCommitPointer = null;
        if (log.getLatest() != null)
            headCommitPointer = commitCallback.commitPointerFromLog(log.getLatest().getMessage());
        DirectoryBox root;
        if (headCommitPointer == null) {
            root = DirectoryBox.create();
        } else {
            headCommit = CommitBox.read(transaction.getCommitAccessor(), headCommitPointer);
            root = DirectoryBox.read(transaction.getTreeAccessor(), headCommit.getTree());
        }
        this.treeAccessor = new TreeAccessor(root, transaction);
    }

    static public ChunkSplitter defaultNodeSplitter(int targetChunkSize) {
        float kFactor = (32f) / (32 * 3 + 8);
        return new RabinSplitter((int)(kFactor * targetChunkSize),
                (int)(kFactor * RabinSplitter.CHUNK_1KB), (int)(kFactor * RabinSplitter.CHUNK_128KB * 5));
    }

    public CommitBox getHeadCommit() {
        return headCommit;
    }

    public String getBranch() {
        return branch;
    }

    private File getBranchDir() {
        return new File(dir, "branches");
    }

    public ICommitCallback getCommitCallback() {
        return commitCallback;
    }

    public IRepoChunkAccessors getChunkAccessors() {
        return accessors;
    }

    private DirectoryBox getDirBox(String path) throws IOException {
        try {
            DirectoryBox.Entry entry = treeAccessor.get(path);
            if (entry == null)
                return null;
            assert !entry.isFile();
            return  (DirectoryBox)entry.getObject();
        } catch (CryptoException e) {
            throw new IOException(e.getMessage());
        }
    }

    public List<String> listFiles(String path) throws IOException {
        DirectoryBox directoryBox = getDirBox(path);
        if (directoryBox == null)
            return Collections.emptyList();
        List<String> entries = new ArrayList<>();
        for (DirectoryBox.Entry fileEntry : directoryBox.getFiles())
            entries.add(fileEntry.getName());
        return entries;
    }

    public List<String> listDirectories(String path) throws IOException {
        DirectoryBox directoryBox = getDirBox(path);
        if (directoryBox == null)
            return Collections.emptyList();
        List<String> entries = new ArrayList<>();
        for (DirectoryBox.Entry dirEntry : directoryBox.getDirs())
            entries.add(dirEntry.getName());
        return entries;
    }

    public byte[] readBytes(String path) throws IOException, CryptoException {
        return treeAccessor.read(path);
    }

    public void writeBytes(String path, byte[] bytes) throws IOException, CryptoException {
        treeAccessor.put(path, writeToFileBox(path, bytes));
    }

    private FileBox writeToFileBox(String path, byte[] data) throws IOException {
        FileBox file = FileBox.create(transaction.getFileAccessor(path), defaultNodeSplitter(RabinSplitter.CHUNK_8KB));
        ChunkContainer chunkContainer = file.getDataContainer();
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer,
                chunkSplitter);
        containerOutputStream.write(data);
        containerOutputStream.flush();
        return file;
    }

    static HashValue put(TypedBlob blob, IChunkAccessor accessor) throws IOException, CryptoException {
        ChunkSplitter nodeSplitter = Repository.defaultNodeSplitter(RabinSplitter.CHUNK_8KB);
        ChunkContainer chunkContainer = new ChunkContainer(accessor, nodeSplitter);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.getBoxPointer().getBoxHash();
    }

    private void copyMissingCommits(CommitBox commitBox,
                                    IRepoChunkAccessors.ITransaction source,
                                    IRepoChunkAccessors.ITransaction target)
            throws IOException, CryptoException {
        // TODO: test
        // copy to their transaction
        ChunkStore.Transaction targetTransaction = target.getRawAccessor();
        if (targetTransaction.contains(commitBox.getBoxPointer().getBoxHash()))
            return;
        for (BoxPointer parent : commitBox.getParents()) {
            CommitBox parentCommit = CommitBox.read(source.getCommitAccessor(), parent);
            copyMissingCommits(parentCommit, source, target);
        }

        ChunkFetcher chunkFetcher = ChunkFetcher.createLocalFetcher(targetTransaction, source.getRawAccessor());
        chunkFetcher.enqueueGetCommitJob(target, commitBox.getBoxPointer());
        chunkFetcher.fetch();
    }

    public void merge(IRepoChunkAccessors.ITransaction otherTransaction, CommitBox otherBranch)
            throws IOException, CryptoException {
        // TODO: check if the transaction is valid, i.e. contains object compatible with otherBranch?
        assert otherBranch != null;

        // 1) Find common ancestor
        // 2) Pull missing objects into the other transaction
        // 3) Merge head with otherBranch and commit the other transaction
        synchronized (Repository.this) {
            commit();

            if (headCommit == null) {
                // we are empty just use the other branch
                otherTransaction.finishTransaction();
                headCommit = otherBranch;

                transaction.finishTransaction();
                transaction = new LogRepoTransaction(accessors.startTransaction());
                log.add(commitCallback.commitPointerToLog(headCommit.getBoxPointer()), transaction.getObjectsWritten());
                treeAccessor = new TreeAccessor(DirectoryBox.read(transaction.getTreeAccessor(), otherBranch.getTree()),
                        transaction);
                return;
            }
            if (headCommit.hash().equals(otherBranch.hash()))
                return;

            CommonAncestorsFinder.Chains chains = CommonAncestorsFinder.find(transaction.getCommitAccessor(),
                    headCommit, otherTransaction.getCommitAccessor(), otherBranch);
            copyMissingCommits(headCommit, transaction, otherTransaction);

            CommonAncestorsFinder.SingleCommitChain shortestChain = chains.getShortestChain();
            if (shortestChain == null)
                throw new IOException("Branches don't have common ancestor.");
            if (shortestChain.getOldest().hash().equals(headCommit.hash())) {
                // not local commits just use the remote head
                otherTransaction.finishTransaction();
                headCommit = otherBranch;

                transaction.finishTransaction();
                transaction = new LogRepoTransaction(accessors.startTransaction());
                log.add(commitCallback.commitPointerToLog(headCommit.getBoxPointer()), transaction.getObjectsWritten());
                treeAccessor = new TreeAccessor(DirectoryBox.read(transaction.getTreeAccessor(), otherBranch.getTree()),
                        transaction);
                return;
            }

            // merge branches
            treeAccessor = ThreeWayMerge.merge(transaction, transaction, headCommit, otherTransaction,
                    otherBranch, shortestChain.getOldest(), ThreeWayMerge.ourSolver());
            commit("Merge.");
        }
    }

    public BoxPointer commit() throws IOException, CryptoException {
        return commit("Repo commit");
    }

    private boolean needCommit() {
        return treeAccessor.isModified();
    }

    public BoxPointer commit(String message) throws IOException, CryptoException {
        if (!needCommit())
            return null;

        synchronized (Repository.this) {
            BoxPointer rootTree = treeAccessor.build();
            CommitBox commitBox = CommitBox.create();
            commitBox.setTree(rootTree);
            if (headCommit != null)
                commitBox.addParent(headCommit.getBoxPointer());
            commitBox.setCommitMessage(commitCallback.createCommitMessage(message, rootTree, commitBox.getParents()));
            HashValue boxHash = put(commitBox, transaction.getCommitAccessor());
            BoxPointer commitPointer = new BoxPointer(commitBox.hash(), boxHash);
            commitBox.setBoxPointer(commitPointer);
            headCommit = commitBox;

            transaction.finishTransaction();
            log.add(commitCallback.commitPointerToLog(commitPointer), transaction.getObjectsWritten());

            transaction = new LogRepoTransaction(accessors.startTransaction());
            this.treeAccessor.setTransaction(transaction);

            return commitPointer;
        }
    }

    public ChunkStoreBranchLog getBranchLog() throws IOException {
        return log;
    }
}
