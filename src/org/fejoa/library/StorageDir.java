/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.IDatabaseInterface;

import java.io.IOException;
import java.util.List;


class StorageDir {
    final IDatabaseInterface database;
    final String baseDir;

    public StorageDir(StorageDir storageDir) {
        this(storageDir.getDatabase(), storageDir.getBaseDir());
    }

    public StorageDir(IDatabaseInterface database, String baseDir) {
        this.database = database;
        this.baseDir = baseDir;
    }

    public IDatabaseInterface getDatabase() {
        return database;
    }

    public String getBaseDir() {
        return baseDir;
    }

    static public String appendDir(String baseDir, String dir) {
        String newDir = new String(baseDir);
        if (dir.equals(""))
            return baseDir;
        if (!newDir.equals(""))
            newDir += "/";
        newDir += dir;
        return newDir;
    }

    public byte[] readBytes(String path) throws IOException {
        return database.readBytes(getRealPath(path));
    }
    public String readString(String path) throws IOException {
        return new String(database.readBytes(getRealPath(path)));
    }
    public int readInt(String path) throws Exception {
        byte data[] = database.readBytes(getRealPath(path));
        return Integer.parseInt(new String(data));
    }

    public void writeBytes(String path, byte[] data) throws IOException {
        database.writeBytes(getRealPath(path), data);
    }
    public void writeString(String path, String data) throws IOException {
        writeBytes(path, data.getBytes());
    }
    public void writeInt(String path, int data) throws IOException {
        String dataString = "";
        dataString += data;
        writeString(path, dataString);
    }

    private String getRealPath(String path) {
        return appendDir(baseDir, path);
    }

    public boolean remove(String path) {
        return false;
    }

    public List<String> listFiles(String path) throws IOException {
        return database.listFiles(getRealPath(path));
    }

    public List<String> listDirectories(String path) throws IOException {
        return database.listDirectories(getRealPath(path));
    }
}

