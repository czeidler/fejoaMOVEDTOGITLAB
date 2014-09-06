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
    final private ContactPrivate myself;
    private ConnectionInfo connectionInfo;
    final private IDatabaseInterface databaseInterface;

    public RemoteStorageLink(SecureStorageDir storageDir, ContactPrivate myself)
            throws IOException, CryptoException {
        this.storageDir = storageDir;
        this.uid = storageDir.readString("uid");
        this.myself = myself;
        String databasePath = storageDir.readSecureString("database_path");
        String databaseBranch = storageDir.readSecureString("database_branch");
        databaseInterface = SecureStorageDirBucket.get(databasePath, databaseBranch).getDatabase();

        try {
            String serverUser = storageDir.readSecureString("server_user");
            String server = storageDir.readSecureString("server");
            connectionInfo = new ConnectionInfo(server, serverUser, myself);
        } catch (IOException e) {
            // no server specified
        }
    }

    public RemoteStorageLink(SecureStorageDir baseDir, IDatabaseInterface databaseInterface, ContactPrivate myself) {
        this.uid = CryptoHelper.toHex(CryptoHelper.sha1Hash(Crypto.get().generateInitializationVector(40)));
        this.storageDir = new SecureStorageDir(baseDir, uid);
        this.databaseInterface = databaseInterface;
        this.connectionInfo = null;
        this.myself = myself;
    }

    public String getUid() {
        return uid;
    }

    public IDatabaseInterface getDatabaseInterface() {
        return databaseInterface;
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
        storageDir.writeString("uid", uid);
        storageDir.writeSecureString("database_path", databaseInterface.getPath());
        storageDir.writeSecureString("database_branch", databaseInterface.getBranch());

        storageDir.writeSecureString("server_user", connectionInfo.serverUser);
        storageDir.writeSecureString("server", connectionInfo.server);
    }

}
