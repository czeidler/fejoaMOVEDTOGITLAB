/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class StorageDir {
    final private StorageDirCache cache;
    final private String baseDir;

    public interface IListener {
        void onTipChanged(DatabaseDiff diff, String base, String tip);
    }

    public void addListener(IListener listener) {
        cache.addListener(listener);
    }

    /**
     * The StorageDirCache between all StorageDir that are build from the same parent.
     */
    class StorageDirCache extends WeakListenable<StorageDir.IListener> {
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

        public void writeBytes(String path, byte[] data) {
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

        public void commit() throws IOException {
            String base = getDatabase().getTip();

            flush();
            database.commit();

            if (getListeners().size() > 0) {
                String tip = getTip();

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

        public void importPack(byte[] pack, String lastSyncCommit, String tip, int format) throws IOException {
            if (!toAdd.isEmpty())
                throw new IOException("cache must be empty before importing a pack!");

            String base = database.getTip();
            database.importPack(pack, lastSyncCommit, tip, format);

            if (getListeners().size() > 0) {
                DatabaseDiff diff = getDatabase().getDiff(base, tip);
                notifyTipChanged(diff, base, tip);
            }
        }

        public void remove(String path) {
            toDelete.add(path);
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

    private IDatabaseInterface getDatabase() {
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

    public String getLastSyncCommit(String remoteUid, String localBranch) throws IOException {
        return getDatabase().getLastSyncCommit(remoteUid, localBranch);
    }

    public void updateLastSyncCommit(String remoteUid, String branch, String localTipCommit) throws IOException {
        getDatabase().updateLastSyncCommit(remoteUid, branch, localTipCommit);
    }

    public DatabaseDiff getDiff(String baseCommit, String endCommit) throws IOException {
        return getDatabase().getDiff(baseCommit, endCommit);
    }

    public void importPack(byte[] pack, String lastSyncCommit, String tip, int format) throws IOException {
        cache.importPack(pack, lastSyncCommit, tip, format);
    }

    public byte[] exportPack(String lastSyncCommit, String localTipCommit, String remoteTip, int format) throws IOException {
        return getDatabase().exportPack(lastSyncCommit, localTipCommit, remoteTip, format);
    }

}

