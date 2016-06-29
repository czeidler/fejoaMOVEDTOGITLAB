/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.support.StreamHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


class TreeAccessor {
    private DirectoryBox root;
    final private Repository repository;

    public TreeAccessor(DirectoryBox root, Repository repository) throws IOException {
        this.repository = repository;

        this.root = root;
    }

    private String checkPath(String path) {
        while (path.startsWith("/"))
            path = path.substring(1);
        return path;
    }

    public byte[] read(String path) throws IOException {
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
                IChunkAccessor accessor = repository.getChunkAccessor();
                if (entry.getObject() != null) {
                    currentDir = (DirectoryBox)entry.getObject();
                    continue;
                }
                currentDir = new BlobReader(accessor.getChunk(entry.getBoxPointer().getBoxHash())).readDirectory();
            }
        }
        DirectoryBox.Entry fileEntry = currentDir.getEntry(fileName);
        if (fileEntry == null)
            return null;

        FileBox fileBox = (FileBox)repository.get(fileEntry.getBoxPointer().getBoxHash());
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getChunkContainer());
        return StreamHelper.readAll(inputStream);
    }

    public void put(String path, FileBox file) throws IOException {
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
                IChunkAccessor accessor = repository.getChunkAccessor();
                if (entry.getObject() != null) {
                    currentDir = (DirectoryBox)entry.getObject();
                    continue;
                }
                currentDir = new BlobReader(accessor.getChunk(entry.getBoxPointer().getBoxHash())).readDirectory();
            }
        }
        DirectoryBox.Entry fileEntry = currentDir.addFile(fileName, null);
        fileEntry.setObject(file);
    }

    public BoxPointer build() throws IOException {
        return build(root);
    }

    public BoxPointer build(DirectoryBox dir) throws IOException {
        for (DirectoryBox.Entry child : dir.getDirs()) {
            if (child.getBoxPointer() != null)
                continue;
            assert child.getObject() != null;
            child.setBoxPointer(build((DirectoryBox)child.getObject()));
        }
        for (DirectoryBox.Entry child : dir.getFiles()) {
            if (child.getBoxPointer() != null)
                continue;
            assert child.getObject() != null;
            FileBox fileBox = (FileBox)child.getObject();
            HashValue boxHash = repository.put(fileBox);
            child.setBoxPointer(new BoxPointer(fileBox.hash(), boxHash));
        }
        HashValue boxHash = repository.put(dir);
        return new BoxPointer(dir.hash(), boxHash);
    }

    public DirectoryBox getRoot() {
        return root;
    }
}

public class Repository {
    private class Transaction {
        final private List<HashValue> objectsWritten = new ArrayList<>();

        public Transaction() throws IOException {

        }

        public TreeAccessor getTreeAccessor() {
            return treeAccessor;
        }

        public PutResult<HashValue> put(byte[] data) throws IOException {
            PutResult<HashValue> result = chunkAccessor.putChunk(data);
            if (!result.wasInDatabase)
                objectsWritten.add(result.key);

            return result;
        }

        public BoxPointer commit() throws IOException {
            synchronized (Repository.this) {
                BoxPointer boxPointer = treeAccessor.build();

                chunkAccessor.finishTransaction();
                log.add(boxPointer.getBoxHash(), objectsWritten);
                currentTransaction = null;

                return boxPointer;
            }
        }
    }

    final private File dir;
    final private String branch;
    final private ChunkStoreBranchLog log;
    private IChunkAccessor chunkAccessor;
    private Transaction currentTransaction;
    final private TreeAccessor treeAccessor;

    public Repository(File dir, String branch, IChunkAccessor chunkAccessor) throws IOException {
        this.dir = dir;
        this.branch = branch;
        this.chunkAccessor = chunkAccessor;
        this.log = new ChunkStoreBranchLog(new File(getBranchDir(), branch));

        HashValue rootBoxHash = null;
        if (log.getLatest() != null)
            rootBoxHash = log.getLatest().getTip();
        DirectoryBox root;
        if (rootBoxHash == null)
            root = DirectoryBox.create();
        else
            root = new BlobReader(getChunkAccessor().getChunk(rootBoxHash)).readDirectory();
        this.treeAccessor = new TreeAccessor(root, Repository.this);
    }

    public IChunkAccessor getChunkAccessor() {
        return chunkAccessor;
    }

    private File getBranchDir() {
        return new File(dir, "branches");
    }

    private void ensureTransaction() throws IOException {
        synchronized (this) {
            if (currentTransaction != null)
                return;
            chunkAccessor.startTransaction();
            currentTransaction = new Transaction();
        }
    }

    private IChunkAccessor getChunkContainerAccessor() {
        return new IChunkAccessor() {
            @Override
            public DataInputStream getChunk(HashValue hash) throws IOException {
                return chunkAccessor.getChunk(hash);
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data) throws IOException {
                return currentTransaction.put(data);
            }

            @Override
            public void startTransaction() throws IOException {

            }

            @Override
            public void finishTransaction() throws IOException {

            }
        };
    }

    protected TypedBlob get(HashValue hashValue) throws IOException {
        IChunkAccessor containerAccessor = getChunkContainerAccessor();
        ChunkContainer chunkContainer = new ChunkContainer(containerAccessor, hashValue);
        BlobReader blobReader = new BlobReader(new ChunkContainerInputStream(chunkContainer));
        return blobReader.read(containerAccessor);
    }

    public HashValue put(TypedBlob blob) throws IOException {
        ensureTransaction();

        ChunkContainer chunkContainer = new ChunkContainer(getChunkContainerAccessor());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.hash();
    }

    public byte[] readBytes(String path) throws IOException {
        return treeAccessor.read(path);
    }

    public void writeBytes(String path, byte[] bytes) throws IOException {
        ensureTransaction();

        TreeAccessor treeAccessor = currentTransaction.getTreeAccessor();
        treeAccessor.put(path, writeToFileBox(bytes));
    }

    private FileBox writeToFileBox(byte[] data) throws IOException {
        FileBox file = FileBox.create(getChunkAccessor());
        ChunkContainer chunkContainer = file.getChunkContainer();
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer);
        containerOutputStream.write(data);
        containerOutputStream.flush();
        return file;
    }

    public BoxPointer commit() throws IOException {
        synchronized (Repository.this) {
            BoxPointer boxPointer = currentTransaction.commit();
            currentTransaction = null;
            return boxPointer;
        }
    }

    public ChunkStoreBranchLog getBranchLog(String name) throws IOException {
        return new ChunkStoreBranchLog(new File(getBranchDir(), name));
    }
}
