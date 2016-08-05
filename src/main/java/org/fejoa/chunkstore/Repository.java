/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StreamHelper;

import java.io.*;
import java.util.Collection;


class TreeAccessor {
    private boolean modified = false;
    private DirectoryBox root;
    private IRepoChunkAccessors.ITransaction transaction;

    public TreeAccessor(DirectoryBox root, IRepoChunkAccessors.ITransaction transaction) throws IOException {
        this.transaction = transaction;

        this.root = root;
    }

    public boolean isModified() {
        return modified;
    }

    public void setTransaction(IRepoChunkAccessors.ITransaction transaction) {
        this.transaction = transaction;
    }

    private String checkPath(String path) {
        while (path.startsWith("/"))
            path = path.substring(1);
        return path;
    }

    private BoxPointer put(FileBox fileBox) throws IOException, CryptoException {
        fileBox.flush();
        return fileBox.getDataContainer().getBoxPointer();
    }

    public byte[] read(String path) throws IOException, CryptoException {
        path = checkPath(path);
        String[] parts = path.split("/");
        String fileName = parts[parts.length - 1];
        DirectoryBox currentDir = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String subDir = parts[i];
            DirectoryBox.Entry entry = currentDir.getEntry(subDir);
            if (entry == null) {
                return null;
            } else {
                if (entry.getObject() != null) {
                    currentDir = (DirectoryBox)entry.getObject();
                    continue;
                }
                IChunkAccessor accessor = transaction.getTreeAccessor();
                currentDir = DirectoryBox.read(accessor, entry.getDataPointer());
            }
        }
        DirectoryBox.Entry fileEntry = currentDir.getEntry(fileName);
        if (fileEntry == null)
            return null;

        FileBox fileBox = FileBox.read(transaction.getFileAccessor(path), fileEntry.getDataPointer());
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getDataContainer());
        return StreamHelper.readAll(inputStream);
    }

    public void put(String path, FileBox file) throws IOException, CryptoException {
        this.modified = true;
        path = checkPath(path);
        String[] parts = path.split("/");
        String fileName = parts[parts.length - 1];
        DirectoryBox currentDir = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String subDir = parts[i];
            DirectoryBox.Entry entry = currentDir.getEntry(subDir);
            if (entry == null) {
                DirectoryBox subDirBox = DirectoryBox.create();
                DirectoryBox.Entry dirEntry = currentDir.addDir(subDir, null);
                dirEntry.setObject(subDirBox);
                currentDir = subDirBox;
            } else {
                if (entry.getObject() != null) {
                    currentDir = (DirectoryBox)entry.getObject();
                    continue;
                }
                IChunkAccessor accessor = transaction.getTreeAccessor();
                currentDir = DirectoryBox.read(accessor, entry.getDataPointer());
            }
        }
        DirectoryBox.Entry fileEntry = currentDir.addFile(fileName, null);
        fileEntry.setObject(file);
    }

    public BoxPointer build() throws IOException, CryptoException {
        modified = false;
        return build(root, "");
    }

    private BoxPointer build(DirectoryBox dir, String path) throws IOException, CryptoException {
        for (DirectoryBox.Entry child : dir.getDirs()) {
            if (child.getDataPointer() != null)
                continue;
            assert child.getObject() != null;
            child.setDataPointer(build((DirectoryBox)child.getObject(), path + "/" + child.getName()));
        }
        for (DirectoryBox.Entry child : dir.getFiles()) {
            if (child.getDataPointer() != null)
                continue;
            assert child.getObject() != null;
            FileBox fileBox = (FileBox)child.getObject();
            BoxPointer dataPointer = put(fileBox);
            child.setDataPointer(dataPointer);
        }
        HashValue boxHash = Repository.put(dir, transaction.getTreeAccessor());
        return new BoxPointer(dir.hash(), boxHash);
    }

    public DirectoryBox getRoot() {
        return root;
    }
}

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

    public void merge(ChunkStore.Transaction otherTransaction, CommitBox otherBranch) throws IOException, CryptoException {
        // TODO: check if the transaction is valid, i.e. contains object compatible with otherBranch?
        assert otherBranch != null;

        // 1) Find common ancestor
        // 2) Pull missing objects into the other transaction
        // 3) Merge head with otherBranch and commit the other transaction
        synchronized (Repository.this) {
            commit();

            if (headCommit == null) {
                // we are empty just use the other branch
                otherTransaction.commit();
                headCommit = otherBranch;

                transaction.finishTransaction();
                transaction = new LogRepoTransaction(accessors.startTransaction());
                log.add(commitCallback.commitPointerToLog(headCommit.getBoxPointer()), transaction.getObjectsWritten());
                treeAccessor = new TreeAccessor(DirectoryBox.read(transaction.getTreeAccessor(), otherBranch.getTree()),
                        transaction);
            }
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
