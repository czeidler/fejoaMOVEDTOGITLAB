/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.fejoa.library2.Remote;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;


public class OutgoingCommandQueue extends CommandQueue<OutgoingCommandQueue.Entry> {
    static public class Entry extends CommandQueue.Entry {
        final static private String USER_KEY = "user";
        final static private String SERVER_KEY = "server";

        private String user;
        private String server;

        public Entry() {
            super();
        }

        public Entry(byte[] data, String user, String server) {
            super(data);

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

        public String getUser() {
            return user;
        }

        public String getServer() {
            return server;
        }
    }

    public OutgoingCommandQueue(StorageDir dir) throws IOException {
        super(dir);
    }

    public void post(ICommand command, Remote receiver, boolean commit) throws IOException {
        post(command, receiver.getUser(), receiver.getServer(), commit);
    }

    public void post(ICommand command, String user, String server, boolean commit)
            throws IOException {

        OutgoingCommandQueue.Entry entry = new OutgoingCommandQueue.Entry(command.getCommand(), user, server);
        addCommand(entry);

        if (commit)
            commit();
    }

    public void post(ICommand command, String user, String server)
            throws IOException {
        post(command, user, server, true);
    }

    @Override
    protected OutgoingCommandQueue.Entry instantiate() {
        return new Entry();
    }
}
