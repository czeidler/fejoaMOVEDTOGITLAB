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

import java.io.IOException;


public class TreeAccessor {
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

    public DirectoryBox.Entry get(String path) throws IOException, CryptoException {
        path = checkPath(path);
        String[] parts = path.split("/");
        String entryName = parts[parts.length - 1];
        DirectoryBox.Entry currentDir = get(parts, parts.length - 1);
        if (currentDir == null)
            return null;
        if (entryName.equals(""))
            return currentDir;
        return ((DirectoryBox)currentDir.getObject()).getEntry(entryName);
    }

    /**
     * @param parts List of directories
     * @param nDirs Number of dirs in parts that should be follow
     * @return null or an entry pointing to the request directory, the object is loaded
     * @throws IOException
     * @throws CryptoException
     */
    public DirectoryBox.Entry get(String[] parts, int nDirs) throws IOException, CryptoException {
        DirectoryBox.Entry entry = null;
        DirectoryBox currentDir = root;
        for (int i = 0; i < nDirs; i++) {
            String subDir = parts[i];
            entry = currentDir.getEntry(subDir);
            if (entry == null || entry.isFile())
                return null;

            if (entry.getObject() != null) {
                currentDir = (DirectoryBox)entry.getObject();
                continue;
            }
            IChunkAccessor accessor = transaction.getTreeAccessor();
            currentDir = DirectoryBox.read(accessor, entry.getDataPointer());
        }
        if (currentDir == root) {
            entry = new DirectoryBox.Entry("", null, false);
            entry.setObject(root);
        }
        return entry;
    }

    public byte[] read(String path) throws IOException, CryptoException {
        DirectoryBox.Entry fileEntry = get(path);
        assert fileEntry.isFile();

        FileBox fileBox = FileBox.read(transaction.getFileAccessor(path), fileEntry.getDataPointer());
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getDataContainer());
        return StreamHelper.readAll(inputStream);
    }

    public DirectoryBox.Entry put(String path, BoxPointer dataPointer, boolean isFile) throws IOException,
            CryptoException {
        DirectoryBox.Entry entry = new DirectoryBox.Entry("", dataPointer, isFile);
        put(path, entry);
        return entry;
    }

    public void put(String path, DirectoryBox.Entry entry) throws IOException, CryptoException {
        this.modified = true;
        path = checkPath(path);
        String[] parts = path.split("/");
        String fileName = parts[parts.length - 1];
        DirectoryBox currentDir = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String subDir = parts[i];
            DirectoryBox.Entry currentEntry = currentDir.getEntry(subDir);
            if (currentEntry == null) {
                DirectoryBox subDirBox = DirectoryBox.create();
                DirectoryBox.Entry dirEntry = currentDir.addDir(subDir, null);
                dirEntry.setObject(subDirBox);
                currentDir = subDirBox;
            } else {
                if (currentEntry.getObject() != null) {
                    currentDir = (DirectoryBox)currentEntry.getObject();
                    continue;
                }
                IChunkAccessor accessor = transaction.getTreeAccessor();
                currentDir = DirectoryBox.read(accessor, currentEntry.getDataPointer());
            }
        }
        entry.setName(fileName);
        currentDir.put(fileName, entry);
    }

    public void put(String path, FileBox file) throws IOException, CryptoException {
        DirectoryBox.Entry entry = put(path, null, true);
        entry.setObject(file);
    }

    public DirectoryBox.Entry remove(String path) throws IOException, CryptoException {
        path = checkPath(path);
        String[] parts = path.split("/");
        String entryName = parts[parts.length - 1];
        DirectoryBox.Entry currentDir = get(parts, parts.length - 1);
        if (currentDir == null)
            return null;
        // invalidate entry
        currentDir.setDataPointer(null);
        DirectoryBox directoryBox = (DirectoryBox)currentDir.getObject();
        return directoryBox.remove(entryName);
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
