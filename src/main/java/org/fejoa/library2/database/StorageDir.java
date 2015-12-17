/*
 * Copyright 2014-2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.database;

import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.IDatabaseInterface;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.*;


public class StorageDir {
    final private StorageDirCache cache;
    final private String baseDir;
    private IIOFilter filter;

    public interface IIOFilter {
        byte[] writeFilter(byte[] bytes) throws IOException;
        byte[] readFilter(byte[] bytes) throws IOException;
    }

    public interface IListener {
        void onTipChanged(DatabaseDiff diff, String base, String tip);
    }

    public void addListener(IListener listener) {
        cache.addListener(listener);
    }

    public void removeListener(IListener listener) {
        cache.removeListener(listener);
    }

    /**
     * The StorageDirCache is shared between all StorageDir that are build from the same parent.
     */
    static class StorageDirCache extends WeakListenable<StorageDir.IListener> {
        final private IDatabaseInterface database;
        final private Map<String, byte[]> toAdd = new HashMap<>();
        final private List<String> toDelete = new ArrayList<>();

        private void notifyTipChanged(DatabaseDiff diff, String base, String tip) {
            for (IListener listener : getListeners())
                listener.onTipChanged(diff, base, tip);
        }

        public StorageDirCache(IDatabaseInterface database) {
            this.database = database;
        }

        public IDatabaseInterface getDatabase() {
            return database;
        }

        public void writeBytes(String path, byte[] data) throws IOException {
            // process deleted items before adding new items
            if (toDelete.size() > 0)
                flush();
            this.toAdd.put(path, data);
        }

        public byte[] readBytes(String path) throws IOException {
            if (toAdd.containsKey(path))
                return toAdd.get(path);
            return database.readBytes(path);
        }

        public void flush() throws IOException {
            for (Map.Entry<String, byte[]> entry : toAdd.entrySet())
                database.writeBytes(entry.getKey(), entry.getValue());
            toAdd.clear();

            for (String path : toDelete)
                database.remove(path);
            toDelete.clear();
        }

        private boolean needsCommit() {
            if (toAdd.size() == 0 && toDelete.size() == 0)
                return false;
            return true;
        }

        public void commit() throws IOException {
            if (!needsCommit())
                return;
            flush();
            String base = getDatabase().getTip();
            database.commit();

            if (getListeners().size() > 0) {
                String tip = getDatabase().getTip();
                DatabaseDiff diff = getDatabase().getDiff(base, tip);
                notifyTipChanged(diff, base, tip);
            }
        }

        public void merge(String theirCommit) throws IOException {
            commit();

            String base = getDatabase().getTip();
            database.merge(theirCommit);

            if (getListeners().size() > 0) {
                String tip = getDatabase().getTip();
                DatabaseDiff diff = getDatabase().getDiff(base, tip);
                notifyTipChanged(diff, base, tip);
            }
        }

        public List<String> listFiles(String path) throws IOException {
            flush();
            return database.listFiles(path);
        }

        public List<String> listDirectories(String path) throws IOException {
            flush();
            return database.listDirectories(path);
        }

        public void remove(String path) {
            toDelete.add(path);
        }
    }

    public StorageDir(StorageDir storageDir) {
        this(storageDir, storageDir.baseDir, true);
    }

    public StorageDir(StorageDir storageDir, String baseDir) {
        this(storageDir, baseDir, false);
    }

    public StorageDir(StorageDir storageDir, String baseDir, boolean absoluteBaseDir) {
        if (absoluteBaseDir)
            this.baseDir = baseDir;
        else
            this.baseDir = appendDir(storageDir.baseDir, baseDir);
        this.cache = storageDir.cache;
        this.filter = storageDir.filter;
    }

    public StorageDir(IDatabaseInterface database, String baseDir) {
        this.baseDir = baseDir;
        this.cache = new StorageDirCache(database);
    }

    public void setFilter(IIOFilter filter) {
        this.filter = filter;
    }

    public IDatabaseInterface getDatabase() {
        return cache.getDatabase();
    }

    public String getBaseDir() {
        return baseDir;
    }

    static public String appendDir(String baseDir, String dir) {
        String newDir = baseDir;
        if (dir.equals(""))
            return baseDir;
        if (!newDir.equals(""))
            newDir += "/";
        newDir += dir;
        return newDir;
    }

    public byte[] readBytes(String path) throws IOException {
        byte[] bytes = cache.readBytes(getRealPath(path));
        if (filter != null)
            return filter.readFilter(bytes);
        return bytes;
    }

    public void writeBytes(String path, byte[] data) throws IOException {
        if (filter != null)
            data = filter.writeFilter(data);
        cache.writeBytes(getRealPath(path), data);
    }

    public String readString(String path) throws IOException {
        return new String(readBytes(path));
    }

    public int readInt(String path) throws IOException {
        byte data[] = readBytes(path);
        return Integer.parseInt(new String(data));
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

    public void remove(String path) {
        cache.remove(getRealPath(path));
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
        return getDatabase().getTip();
    }

    public String getDatabasePath() {
        return getDatabase().getPath();
    }

    public String getBranch() {
        return getDatabase().getBranch();
    }

    public DatabaseDiff getDiff(String baseCommit, String endCommit) throws IOException {
        return getDatabase().getDiff(baseCommit, endCommit);
    }

    public void merge(String theirCommit) throws IOException {
        cache.merge(theirCommit);
    }
}
