/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;


import org.fejoa.library2.database.StorageDir;

import java.io.IOException;


public class RemoteList extends AbstractStorageDirList {
    static final private String DEFAULT_REMOTE_KEY = "default";

    static class RemoteEntry implements AbstractStorageDirList.IEntry {
        static final private String ID_KEY = "id";
        static final private String USER_KEY = "user";
        static final private String SERVER_KEY = "server";

        private String uid;
        private String user;
        private String server;

        @Override
        public void write(StorageDir dir) throws IOException {
            dir.writeString(ID_KEY, uid);
            dir.writeString(USER_KEY, user);
            dir.writeString(SERVER_KEY, server);
        }

        @Override
        public void read(StorageDir dir) throws IOException {
            uid = dir.readString(ID_KEY);
            user = dir.readString(USER_KEY);
            server = dir.readString(SERVER_KEY);
        }
    }

    static public RemoteList create(StorageDir dir) {
        return new RemoteList(dir);
    }

    static public RemoteList load(StorageDir dir) {
        RemoteList list = new RemoteList(dir);
        list.load();
        return list;
    }

    private RemoteEntry defaultRemote = null;

    protected RemoteList(StorageDir storageDir) {
        super(storageDir);
    }

    @Override
    protected IEntry instantiate(StorageDir dir) {
        return new RemoteEntry();
    }

    @Override
    protected void load() {
        super.load();

        String defaultId;
        try {
            defaultId = storageDir.readString(DEFAULT_REMOTE_KEY);
        } catch (IOException e) {
            return;
        }
        defaultRemote = (RemoteEntry)map.get(defaultId);
    }

    public void setDefault(RemoteEntry entry) throws IOException {
        defaultRemote = entry;
        storageDir.writeString(DEFAULT_REMOTE_KEY, entry.uid);
    }

    public RemoteEntry getDefault() {
        return defaultRemote;
    }
}
