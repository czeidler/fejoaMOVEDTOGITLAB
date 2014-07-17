/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.SecureStorageDirBucket;
import org.fejoa.library.SecureStorageDir;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IDatabaseInterface;

import java.io.IOException;


public class RemoteStorageLink {
    final private SecureStorageDir storageDir;
    final private String uid;
    private RemoteConnection remoteConnection;
    private String serverUser;
    final private ContactPrivate myself;
    final private IDatabaseInterface databaseInterface;

    public RemoteStorageLink(SecureStorageDir storageDir, ContactPrivate myself)
            throws IOException, CryptoException {
        this.storageDir = storageDir;
        this.myself = myself;
        this.uid = storageDir.readString("uid");
        String databasePath = storageDir.readSecureString("database_path");
        String databaseBranch = storageDir.readSecureString("database_branch");
        databaseInterface = SecureStorageDirBucket.get(databasePath, databaseBranch).getDatabase();

        String url = storageDir.readSecureString("url");
        remoteConnection = new RemoteConnection(RemoteRequestFactory.getRemoteRequest(url));
    }

    public RemoteStorageLink(SecureStorageDir baseDir, IDatabaseInterface databaseInterface, ContactPrivate myself) {
        this.uid = CryptoHelper.toHex(CryptoHelper.sha1Hash(Crypto.get().generateInitializationVector(40)));
        this.storageDir = new SecureStorageDir(baseDir, uid);
        this.databaseInterface = databaseInterface;
        this.myself = myself;
    }

    public String getUid() {
        return uid;
    }

    public ContactPrivate getMyself() {
        return myself;
    }

    public String getServerUser() {
        return serverUser;
    }

    public RemoteConnection getRemoteConnection() {
        return remoteConnection;
    }

    public IDatabaseInterface getDatabaseInterface() {
        return databaseInterface;
    }

    public void setTo(RemoteConnection remoteConnection, String serverUser) {
        this.remoteConnection = remoteConnection;
        this.serverUser = serverUser;
    }

    public void write() throws IOException, CryptoException {
        storageDir.writeString("uid", uid);
        storageDir.writeSecureString("database_path", databaseInterface.getPath());
        storageDir.writeSecureString("database_branch", databaseInterface.getBranch());

        if (remoteConnection != null) {
            String url = remoteConnection.getRemoteRequest().getUrl();
            storageDir.writeSecureString("url", url);
        }
    }
}
