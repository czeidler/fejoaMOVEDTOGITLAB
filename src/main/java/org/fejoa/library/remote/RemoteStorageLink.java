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

    final private String UID_KEY = "uid";
    final private String STORAGE_UID_KEY = "storageUid";
    final private String STORAGE_BRANCH_KEY = "storageBranch";
    final private String SERVER_USER_KEY = "serverUser";
    final private String SERVER_KEY = "server";

    /**
     * Read an existing storage link from a storage dir.
     *
     * @param environment
     * @param linkStorage
     * @param myself
     * @throws IOException
     * @throws CryptoException
     */
    public RemoteStorageLink(FejoaEnvironment environment, SecureStorageDir linkStorage, ContactPrivate myself)
            throws IOException, CryptoException {
        this.linkStorage = linkStorage;
        this.uid = linkStorage.readString(UID_KEY);
        this.myself = myself;
        storageUid = linkStorage.readSecureString(STORAGE_UID_KEY);
        String databaseBranch = linkStorage.readSecureString(STORAGE_BRANCH_KEY);
        localStorage = environment.getByStorageId(storageUid, databaseBranch);

        try {
            String serverUser = linkStorage.readSecureString(SERVER_USER_KEY);
            String server = linkStorage.readSecureString(SERVER_KEY);
            connectionInfo = new ConnectionInfo(server, serverUser, myself);
        } catch (IOException e) {
            // no server specified
        }
    }

    /**
     * Create a new storage link.
     *
     * @param baseDir
     * @param storage
     * @param myself
     */
    public RemoteStorageLink(SecureStorageDir baseDir, IStorageUid storage, ContactPrivate myself) {
        this.uid = CryptoHelper.toHex(CryptoHelper.sha1Hash(Crypto.get().generateInitializationVector(40)));
        this.linkStorage = new SecureStorageDir(baseDir, uid);
        this.localStorage = storage.getStorageDir();
        this.storageUid = storage.getUid();
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
        linkStorage.writeString(UID_KEY, uid);
        linkStorage.writeSecureString(STORAGE_UID_KEY, storageUid);
        linkStorage.writeSecureString(STORAGE_BRANCH_KEY, localStorage.getBranch());

        if (connectionInfo != null) {
            linkStorage.writeSecureString(SERVER_USER_KEY, connectionInfo.serverUser);
            linkStorage.writeSecureString(SERVER_KEY, connectionInfo.server);
        }
    }

}
