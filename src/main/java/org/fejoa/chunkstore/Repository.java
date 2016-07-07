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
    final private IRepoChunkAccessors accessors;

    public TreeAccessor(DirectoryBox root, IRepoChunkAccessors accessors) throws IOException {
        this.accessors = accessors;

        this.root = root;
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

    private HashValue put(TypedBlob blob, IChunkAccessor accessor) throws IOException, CryptoException {
        ChunkContainer chunkContainer = new ChunkContainer(accessor);
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
                IChunkAccessor accessor = accessors.getTreeAccessor();
                currentDir = new BlobReader(accessor.getChunk(entry.getBoxPointer())).readDirectory();
            }
        }
        DirectoryBox.Entry fileEntry = currentDir.getEntry(fileName);
        if (fileEntry == null)
            return null;

        FileBox fileBox = (FileBox)get(fileEntry.getBoxPointer(), accessors.getFileAccessor(path));
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getChunkContainer());
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
                IChunkAccessor accessor = accessors.getTreeAccessor();
                currentDir = new BlobReader(accessor.getChunk(entry.getBoxPointer())).readDirectory();
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
            if (child.getBoxPointer() != null)
                continue;
            assert child.getObject() != null;
            child.setBoxPointer(build((DirectoryBox)child.getObject(), path + "/" + child.getName()));
        }
        for (DirectoryBox.Entry child : dir.getFiles()) {
            if (child.getBoxPointer() != null)
                continue;
            assert child.getObject() != null;
            FileBox fileBox = (FileBox)child.getObject();
            HashValue boxHash = put(fileBox, accessors.getFileAccessor(path + "/" + child.getName()));
            child.setBoxPointer(new BoxPointer(fileBox.hash(), boxHash));
        }
        HashValue boxHash = put(dir, accessors.getTreeAccessor());
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
    private boolean transactionOngoing = false;
    private LogRepoChunkAccessors chunkAccessors;
    final private TreeAccessor treeAccessor;
    final private ChunkSplitter splitter = new RabinSplitter();

    public Repository(File dir, String branch, IRepoChunkAccessors chunkAccessors) throws IOException, CryptoException {
        this.dir = dir;
        this.branch = branch;
        this.chunkAccessors = new LogRepoChunkAccessors(chunkAccessors);
        this.log = new ChunkStoreBranchLog(new File(getBranchDir(), branch));

        HashValue rootBoxHash = null;
        if (log.getLatest() != null)
            rootBoxHash = log.getLatest().getTip();
        DirectoryBox root;
        if (rootBoxHash == null)
            root = DirectoryBox.create();
        else
            root = new BlobReader(chunkAccessors.getCommitAccessor().getChunk(new BoxPointer(null, rootBoxHash)))
                    .readDirectory();
        this.treeAccessor = new TreeAccessor(root, chunkAccessors);
    }

    private File getBranchDir() {
        return new File(dir, "branches");
    }

    public IRepoChunkAccessors getChunkAccessors() {
        return chunkAccessors;
    }

    private void ensureTransaction() throws IOException {
        synchronized (this) {
            if (transactionOngoing)
                return;
            chunkAccessors.startTransaction();
            transactionOngoing = true;
        }
    }

    public byte[] readBytes(String path) throws IOException, CryptoException {
        return treeAccessor.read(path);
    }

    public void writeBytes(String path, byte[] bytes) throws IOException, CryptoException {
        ensureTransaction();

        treeAccessor.put(path, writeToFileBox(path, bytes));
    }

    private FileBox writeToFileBox(String path, byte[] data) throws IOException {
        FileBox file = FileBox.create(chunkAccessors.getFileAccessor(path));
        ChunkContainer chunkContainer = file.getChunkContainer();
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer, splitter);
        containerOutputStream.write(data);
        containerOutputStream.flush();
        return file;
    }

    public BoxPointer commit() throws IOException, CryptoException {
        synchronized (Repository.this) {
            BoxPointer boxPointer = treeAccessor.build();

            chunkAccessors.finishTransaction();
            log.add(boxPointer.getBoxHash(), chunkAccessors.getObjectsWritten());
            transactionOngoing = false;

            return boxPointer;
        }
    }

    public ChunkStoreBranchLog getBranchLog(String name) throws IOException {
        return new ChunkStoreBranchLog(new File(getBranchDir(), name));
    }
}
