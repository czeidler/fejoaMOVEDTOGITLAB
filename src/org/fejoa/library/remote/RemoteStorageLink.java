/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.DatabaseBucket;
import org.fejoa.library.SecureStorageDir;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IDatabaseInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class RemoteStorageLink {
    final String uid;
    final private List<RemoteConnection> remoteConnections = new ArrayList<>();
    final private IDatabaseInterface databaseInterface;

    public RemoteStorageLink(SecureStorageDir loadFromDir) throws IOException, CryptoException {
        this.uid = loadFromDir.readString("uid");
        String databasePath = loadFromDir.readSecureString("database_path");
        String databaseBranch = loadFromDir.readSecureString("database_branch");
        databaseInterface = DatabaseBucket.get(databasePath, databaseBranch);

        List<String> remoteConnectionIds = loadFromDir.listDirectories("");
        for (String id : remoteConnectionIds) {
            SecureStorageDir subDir = new SecureStorageDir(loadFromDir, id);
            String url = subDir.readSecureString("url");
            RemoteConnection remoteConnection = new RemoteConnection(RemoteRequestFactory.getRemoteRequest(url));
            remoteConnections.add(remoteConnection);
        }
    }

    public RemoteStorageLink(IDatabaseInterface databaseInterface) {
        this.uid = new String(CryptoHelper.sha256Hash(Crypto.get().generateInitializationVector(40)));
        this.databaseInterface = databaseInterface;
    }

    public String getUid() {
        return uid;
    }

    public List<RemoteConnection> getRemoteConnections() {
        return remoteConnections;
    }

    public IDatabaseInterface getDatabaseInterface() {
        return databaseInterface;
    }

    public void addRemoteConnection(RemoteConnection remoteConnection) {
        remoteConnections.add(remoteConnection);
    }

    public void write(SecureStorageDir dir) throws IOException, CryptoException {
        dir.writeString("uid", uid);
        dir.writeSecureString("database_path", databaseInterface.getPath());
        dir.writeSecureString("database_branch", databaseInterface.getBranch());

        for (RemoteConnection remoteConnection : remoteConnections) {
            String url = remoteConnection.getRemoteRequest().getUrl();
            String hash = new String(CryptoHelper.sha1Hash(url.getBytes()));
            dir.writeSecureString(hash + "/url", url);
        }
    }
}
