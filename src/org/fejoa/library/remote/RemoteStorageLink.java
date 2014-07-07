/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.DatabaseBucket;
import org.fejoa.library.SecureStorageDir;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IDatabaseInterface;

import java.io.IOException;


public class RemoteStorageLink {
    final String uid;
    private RemoteConnection remoteConnection;
    private String serverUser;
    final private ContactPrivate myself;
    final private IDatabaseInterface databaseInterface;

    public RemoteStorageLink(SecureStorageDir loadFromDir, ContactPrivate myself) throws IOException, CryptoException {
        this.myself = myself;
        this.uid = loadFromDir.readString("uid");
        String databasePath = loadFromDir.readSecureString("database_path");
        String databaseBranch = loadFromDir.readSecureString("database_branch");
        databaseInterface = DatabaseBucket.get(databasePath, databaseBranch);

        String url = loadFromDir.readSecureString("url");
        remoteConnection = new RemoteConnection(RemoteRequestFactory.getRemoteRequest(url));
    }

    public RemoteStorageLink(IDatabaseInterface databaseInterface, ContactPrivate myself) {
        this.databaseInterface = databaseInterface;
        this.myself = myself;
        this.uid = new String(CryptoHelper.sha256Hash(Crypto.get().generateInitializationVector(40)));
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

    public void write(SecureStorageDir dir) throws IOException, CryptoException {
        dir.writeString("uid", uid);
        dir.writeSecureString("database_path", databaseInterface.getPath());
        dir.writeSecureString("database_branch", databaseInterface.getBranch());

        String url = remoteConnection.getRemoteRequest().getUrl();
        dir.writeSecureString("url", url);
    }
}
