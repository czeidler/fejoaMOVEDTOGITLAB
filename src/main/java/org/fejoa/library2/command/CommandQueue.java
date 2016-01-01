/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library2.Constants;
import org.fejoa.library2.IStorageDirBundle;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


abstract class CommandQueue<T extends CommandQueue.Entry> {
    static class Entry implements IStorageDirBundle {
        final static private String COMMAND_KEY = "command";

        private byte[] data;

        public Entry() {
        }

        public Entry(byte[] data) {
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }

        public String hash() {
            return CryptoHelper.sha1HashHex(getData());
        }

        @Override
        public String toString() {
            return hash();
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

    final protected StorageDir storageDir;

    public CommandQueue(StorageDir dir) throws IOException {
        this.storageDir = new StorageDir(dir);
        this.storageDir.setFilter(null);

        // write dummy file TODO: fix in the database
        dir.writeString(Constants.ID_KEY, dir.getBranch());
    }

    public String getId() {
        return storageDir.getBranch();
    }

    public void commit() throws IOException {
        storageDir.commit();
    }

    public StorageDir getStorageDir() {
        return storageDir;
    }

    protected void addCommand(T command) throws IOException {
        StorageDir dir = new StorageDir(storageDir, command.hash());
        command.write(dir);
    }

    public List<T> getCommands() throws IOException {
        List<T> commands = new ArrayList<>();
        getCommands(storageDir, commands);
        return commands;
    }

    private void getCommands(StorageDir dir, List<T> list) throws IOException {
        List<String> hashes = dir.listDirectories("");
        for (String hash : hashes) {
            try {
                T entry = instantiate();
                entry.read(new StorageDir(dir, hash));
                list.add(entry);
            } catch (IOException e) {
                dir.remove(hash);
            }
        }
    }

    public void removeCommand(T command) {
        storageDir.remove(command.hash());
    }

    abstract protected T instantiate();
}


