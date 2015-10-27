/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;


import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;


public class RemoteList extends AbstractStorageDirList<RemoteList.Entry> {
    static final private String DEFAULT_REMOTE_KEY = "default";

    static public class Entry implements IStorageDirBundle {
        static final private String USER_KEY = "user";
        static final private String SERVER_KEY = "server";

        private String id;
        private String user;
        private String server;

        private Entry() {

        }

        public Entry(String user, String server) {
            this.id = CryptoHelper.generateSha1Id(Crypto.get());
            this.user = user;
            this.server = server;
        }

        public String getId() {
            return id;
        }

        public String getUser() {
            return user;
        }

        public String getServer() {
            return server;
        }

        @Override
        public void write(StorageDir dir) throws IOException {
            dir.writeString(Constants.ID_KEY, id);
            dir.writeString(USER_KEY, user);
            dir.writeString(SERVER_KEY, server);
        }

        @Override
        public void read(StorageDir dir) throws IOException {
            id = dir.readString(Constants.ID_KEY);
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

    private Entry defaultRemote = null;

    protected RemoteList(StorageDir storageDir) {
        super(storageDir);
    }

    @Override
    protected Entry instantiate(StorageDir dir) {
        return new Entry();
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

        defaultRemote = getRemote(defaultId);
    }

    public Entry getRemote(String id) {
        for (Entry entry : map.values()) {
            if (entry.getId().equals(id))
                return entry;
        }
        return null;
    }

    public void setDefault(Entry entry) throws IOException {
        if (!map.containsValue(entry))
            throw new IOException("entry not in list");

        defaultRemote = entry;
        storageDir.writeString(DEFAULT_REMOTE_KEY, entry.getId());
    }

    public Entry getDefault() {
        return defaultRemote;
    }
}
