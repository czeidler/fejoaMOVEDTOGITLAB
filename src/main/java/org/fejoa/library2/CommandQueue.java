/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;


import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.util.Collections;
import java.util.List;


abstract  class CommandQueue<T extends CommandQueue.Entry> {
    static class Entry implements IStorageDirBundle {
        final static private String COMMAND_KEY = "command";

        private String time;
        private byte[] data;

        public Entry(String time) {
            this.time = time;
        }

        public Entry(String time, byte[] data) {
            this.time = time;
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }

        public String hash() {
            return CryptoHelper.sha1HashHex(getData());
        }

        public String getTime() {
            return time;
        }

        @Override
        public void write(StorageDir dir) throws IOException {
            dir.writeBytes(COMMAND_KEY, data);
        }

        @Override
        public void read(StorageDir dir) throws IOException {
            this.data = dir.readBytes(COMMAND_KEY);
        }
    }

    final private StorageDir storageDir;

    public CommandQueue(StorageDir dir) throws IOException {
        this.storageDir = dir;

        // write dummy file TODO: fix in the database
        dir.writeString(Constants.ID_KEY, dir.getBranch());
    }

    public String getId() {
        return storageDir.getBranch();
    }

    public void addCommand(T command) throws IOException {
        long time = System.currentTimeMillis();
        StorageDir dir = new StorageDir(storageDir, Long.toString(time));
        dir = new StorageDir(dir, command.hash());
        command.write(dir);
    }

    public T getNextCommand() throws IOException {
        List<String> times = storageDir.listDirectories("");
        if (times.size() == 0)
            return null;
        Collections.sort(times);
        String oldest = times.get(0);
        StorageDir dir = new StorageDir(storageDir, oldest);
        List<String> hashes = dir.listDirectories("");
        if (hashes.size() == 0) {
            storageDir.remove(oldest);
            return getNextCommand();
        }

        String hash = hashes.get(0);
        try {
            T entry = instantiate(oldest);
            entry.read(new StorageDir(dir, hash));
            return entry;
        } catch (IOException e) {
            dir.remove(hash);
            return getNextCommand();
        }
    }

    public void removeCommand(T command) {
        StorageDir dir = new StorageDir(storageDir, command.getTime());
        dir.remove(command.hash());
    }

    abstract protected T instantiate(String time);
}


class IncomingCommandQueue extends CommandQueue<CommandQueue.Entry> {
    public IncomingCommandQueue(StorageDir dir) throws IOException {
        super(dir);
    }

    @Override
    protected Entry instantiate(String time) {
        return new Entry(time);
    }
}

class OutgoingCommandQueue extends CommandQueue<CommandQueue.Entry> {
    static class Entry extends CommandQueue.Entry {
        final static private String USER_KEY = "user";
        final static private String SERVER_KEY = "server";

        private String user;
        private String server;

        public Entry(String time) {
            super(time);
        }

        public Entry(String time, byte[] data, String user, String server) {
            super(time, data);

            this.user = user;
            this.server = server;
        }

        @Override
        public void write(StorageDir dir) throws IOException {
            super.write(dir);

            dir.writeString(USER_KEY, user);
            dir.writeString(SERVER_KEY, server);
        }

        @Override
        public void read(StorageDir dir) throws IOException {
            super.read(dir);

            user = dir.readString(USER_KEY);
            server = dir.readString(SERVER_KEY);
        }
    }

    public OutgoingCommandQueue(StorageDir dir) throws IOException {
        super(dir);
    }

    @Override
    protected CommandQueue.Entry instantiate(String time) {
        return new Entry(time);
    }
}