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


class TreeAccessor {
    private DirectoryBox root;
    private IRepoChunkAccessors.ITransaction transaction;

    public TreeAccessor(DirectoryBox root, IRepoChunkAccessors.ITransaction transaction) throws IOException {
        this.transaction = transaction;

        this.root = root;
    }

    public void setTransaction(IRepoChunkAccessors.ITransaction transaction) {
        this.transaction = transaction;
    }

    private String checkPath(String path) {
        while (path.startsWith("/"))
            path = path.substring(1);
        return path;
    }

    private TypedBlob get(BoxPointer hashValue, IChunkAccessor accessor) throws IOException, CryptoException {
        ChunkContainer chunkContainer = new ChunkContainer(accessor, hashValue);
        BlobReader blobReader = new BlobReader(new ChunkContainerInputStream(chunkContainer));
        return blobReader.read(accessor);
    }

    private BoxPointer put(FileBox fileBox, IChunkAccessor accessor) throws IOException, CryptoException {
        fileBox.flush();
        return fileBox.getDataContainer().getBoxPointer();
    }

    private HashValue put(TypedBlob blob, IChunkAccessor accessor) throws IOException, CryptoException {
        ChunkSplitter nodeSplitter = Repository.defaultNodeSplitter(RabinSplitter.CHUNK_8KB);
        ChunkContainer chunkContainer = new ChunkContainer(accessor, nodeSplitter);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.getBoxPointer().getBoxHash();
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
                currentDir = new BlobReader(accessor.getChunk(entry.getDataPointer())).readDirectory();
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
                currentDir = new BlobReader(accessor.getChunk(entry.getDataPointer())).readDirectory();
            }
        }
        DirectoryBox.Entry fileEntry = currentDir.addFile(fileName, null);
        fileEntry.setObject(file);
    }

    public BoxPointer build() throws IOException, CryptoException {
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
            BoxPointer dataPointer = put(fileBox, transaction.getFileAccessor(path + "/" + child.getName()));
            child.setDataPointer(dataPointer);
        }
        HashValue boxHash = put(dir, transaction.getTreeAccessor());
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
    final private IRepoChunkAccessors accessors;
    private LogRepoTransaction transaction;
    final private TreeAccessor treeAccessor;
    final private ChunkSplitter chunkSplitter = new RabinSplitter();

    public Repository(File dir, String branch, IRepoChunkAccessors chunkAccessors) throws IOException, CryptoException {
        this.dir = dir;
        this.branch = branch;
        this.accessors = chunkAccessors;
        this.transaction = new LogRepoTransaction(accessors.startTransaction());
        this.log = new ChunkStoreBranchLog(new File(getBranchDir(), branch));

        HashValue rootBoxHash = null;
        if (log.getLatest() != null)
            rootBoxHash = log.getLatest().getTip();
        DirectoryBox root;
        if (rootBoxHash == null)
            root = DirectoryBox.create();
        else
            root = new BlobReader(transaction.getCommitAccessor().getChunk(new BoxPointer(null, rootBoxHash)))
                    .readDirectory();
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

    public BoxPointer commit() throws IOException, CryptoException {
        synchronized (Repository.this) {
            BoxPointer boxPointer = treeAccessor.build();

            transaction.finishTransaction();
            log.add(boxPointer.getBoxHash(), transaction.getObjectsWritten());

            transaction = new LogRepoTransaction(accessors.startTransaction());
            this.treeAccessor.setTransaction(transaction);

            return boxPointer;
        }
    }

    public ChunkStoreBranchLog getBranchLog() throws IOException {
        return log;
    }
}
