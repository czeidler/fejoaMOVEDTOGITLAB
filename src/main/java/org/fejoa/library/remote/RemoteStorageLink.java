/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.IStorageUid;
import org.fejoa.library.database.FejoaEnvironment;
import org.fejoa.library.database.SecureStorageDir;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.StorageDir;

import java.io.IOException;


public class RemoteStorageLink {
    final private String uid;
    final private SecureStorageDir linkStorage;
    final private String storageUid;
    final private ContactPrivate myself;
    private ConnectionInfo connectionInfo;
    final private StorageDir localStorage;

    public RemoteStorageLink(FejoaEnvironment environment, SecureStorageDir linkStorage, ContactPrivate myself)
            throws IOException, CryptoException {
        this.linkStorage = linkStorage;
        this.uid = linkStorage.readString("uid");
        this.myself = myself;
        storageUid = linkStorage.readSecureString("storageUid");
        String databaseBranch = linkStorage.readSecureString("storageBranch");
        localStorage = environment.getByStorageId(storageUid, databaseBranch);

        try {
            String serverUser = linkStorage.readSecureString("serverUser");
            String server = linkStorage.readSecureString("server");
            connectionInfo = new ConnectionInfo(server, serverUser, myself);
        } catch (IOException e) {
            // no server specified
        }
    }

    public RemoteStorageLink(SecureStorageDir baseDir, IStorageUid storeage, ContactPrivate myself) {
        this.uid = CryptoHelper.toHex(CryptoHelper.sha1Hash(Crypto.get().generateInitializationVector(40)));
        this.linkStorage = new SecureStorageDir(baseDir, uid);
        this.localStorage = storeage.getStorageDir();
        this.storageUid = storeage.getUid();
        this.connectionInfo = null;
        this.myself = myself;
    }

    public String getUid() {
        return uid;
    }

    public StorageDir getLocalStorage() {
        return localStorage;
    }

    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    public void setConnectionInfo(String server, String serverUser, ContactPrivate myself) {
        connectionInfo = new ConnectionInfo(server, serverUser, myself);
    }

    public ContactPrivate getMyself() {
        return myself;
    }

    public void write() throws IOException, CryptoException {
        linkStorage.writeString("uid", uid);
        linkStorage.writeSecureString("storageUid", storageUid);
        linkStorage.writeSecureString("storageBranch", localStorage.getBranch());

        if (connectionInfo != null) {
            linkStorage.writeSecureString("serverUser", connectionInfo.serverUser);
            linkStorage.writeSecureString("server", connectionInfo.server);
        }
    }

}
