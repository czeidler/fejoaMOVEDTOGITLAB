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


public class Remote implements IStorageDirBundle {
    private String id;
    private String user;
    private String server;

    Remote() {

    }

    public Remote(String user, String server) {
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
        dir.writeString(Constants.USER_KEY, user);
        dir.writeString(Constants.SERVER_KEY, server);
    }

    @Override
    public void read(StorageDir dir) throws IOException {
        id = dir.readString(Constants.ID_KEY);
        user = dir.readString(Constants.USER_KEY);
        server = dir.readString(Constants.SERVER_KEY);
    }
}
