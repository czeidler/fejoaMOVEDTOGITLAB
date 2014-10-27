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
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class StorageDir {
    final private StorageDirCache cache;
    final private String baseDir;

    class StorageDirCache {
        final private IDatabaseInterface database;
        final private Map<String, byte[]> map = new HashMap<>();

        public StorageDirCache(IDatabaseInterface database) {
            this.database = database;
        }

        public IDatabaseInterface getDatabase() {
            return database;
        }

        public void writeBytes(String path, byte[] data) {
            this.map.put(path, data);
        }

        public byte[] readBytes(String path) throws IOException {
            if (map.containsKey(path))
                return map.get(path);
            return database.readBytes(path);
        }

        public void flush() throws IOException {
            for (Map.Entry<String, byte[]> entry : map.entrySet())
                database.writeBytes(entry.getKey(), entry.getValue());
            map.clear();
        }

        public void commit() throws IOException {
            flush();
            database.commit();
        }

        public List<String> listFiles(String path) throws IOException {
            flush();
            return database.listFiles(path);
        }

        public List<String> listDirectories(String path) throws IOException {
            flush();
            return database.listDirectories(path);
        }
    }

    public StorageDir(StorageDir storageDir, String baseDir, boolean absoluteBaseDir) {
        if (absoluteBaseDir)
            this.baseDir = baseDir;
        else
            this.baseDir = appendDir(storageDir.baseDir, baseDir);
        this.cache = storageDir.cache;
    }

    public StorageDir(IDatabaseInterface database, String baseDir) {
        this.baseDir = baseDir;
        this.cache = new StorageDirCache(database);
    }

    public IDatabaseInterface getDatabase() {
        return cache.getDatabase();
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
        return cache.readBytes(getRealPath(path));
    }
    public String readString(String path) throws IOException {
        return new String(readBytes(path));
    }
    public int readInt(String path) throws Exception {
        byte data[] = readBytes(path);
        return Integer.parseInt(new String(data));
    }

    public void writeBytes(String path, byte[] data) throws IOException {
        cache.writeBytes(getRealPath(path), data);
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
        return cache.listFiles(getRealPath(path));
    }

    public List<String> listDirectories(String path) throws IOException {
        return cache.listDirectories(getRealPath(path));
    }

    public void commit() throws IOException {
        cache.commit();
    }

    public String getTip() throws IOException {
        return cache.getDatabase().getTip();
    }
}

