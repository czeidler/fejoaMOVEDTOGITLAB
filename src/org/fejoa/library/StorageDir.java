/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.IDatabaseInterface;


class StorageDir {
    final IDatabaseInterface database;
    final String baseDir;

    public StorageDir(IDatabaseInterface database, String baseDir) {
        this.database = database;
        if (!baseDir.equals(""))
            baseDir += "/";
        this.baseDir = baseDir;
    }

    public byte[] readBytes(String path) throws Exception {
        return database.readBytes(getRealPath(path));
    }

    public String readString(String path) throws Exception {
        return new String(database.readBytes(getRealPath(path)));
    }

    public int readInt(String path) throws Exception {
        byte data[] = database.readBytes(getRealPath(path));
        return Integer.parseInt(new String(data));
    }

    public void writeBytes(String path, byte[] data) throws Exception {
        database.writeBytes(getRealPath(path), data);
    }

    public void writeString(String path, String data) throws Exception {
        database.writeBytes(getRealPath(path), data.getBytes());
    }

    public void writeInt(String path, int data) throws Exception {
        String dataString = new String();
        dataString += data;
        writeString(getRealPath(path), dataString);
    }

    private String getRealPath(String path) {
        return baseDir + path;
    }
}

class SecureStorageDir extends StorageDir {
    public SecureStorageDir(IDatabaseInterface database, String baseDir) {
        super(database, baseDir);
    }

    public byte[] readSecure(String path) throws Exception {
        return null;
    }

    public void writeSecure(String path, byte[] data) throws Exception {

    }
}