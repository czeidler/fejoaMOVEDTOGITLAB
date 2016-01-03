/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library2.command.*;
import org.fejoa.library2.remote.*;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;


public class Client {
    final private FejoaContext context;
    private ConnectionManager connectionManager;
    private UserData userData;
    private SyncManager syncManager;
    private OutgoingQueueManager outgoingQueueManager;
    private IncomingCommandManager incomingCommandManager;

    private IncomingContactRequestHandler contactRequestHandler = new IncomingContactRequestHandler(this, null);

    public Client(String home) {
        this.context = new FejoaContext(home);
        this.connectionManager = new ConnectionManager(null);
    }

    public void create(String userName, String server, String password) throws IOException, CryptoException {
        userData = UserData.create(context, password);
        Remote remoteRemote = new Remote(userName, server);
        userData.getRemoteList().add(remoteRemote);
        userData.getRemoteList().setDefault(remoteRemote);
    }

    public void open(String password) throws IOException, CryptoException {
        userData = UserData.open(context, password);
    }

    public void commit() throws IOException {
        userData.commit();
    }

    public FejoaContext getContext() {
        return context;
    }

    public UserData getUserData() {
        return userData;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void createAccount(String userName, String password, String server,
                              Task.IObserver<Void, RemoteJob.Result> observer) {
        connectionManager.submit(new CreateAccountJob(userName, password, userData.getId(),
                CryptoSettings.getDefault().masterPassword),
                new ConnectionManager.ConnectionInfo(userName, server),
                new ConnectionManager.AuthInfo(ConnectionManager.AuthInfo.NONE, null),
                observer);
    }

    public void startSyncing(Task.IObserver<TaskUpdate, Void> observer) {
        Remote defaultRemote = getUserData().getRemoteList().getDefault();
        syncManager = new SyncManager(context, getConnectionManager(), defaultRemote);
        syncManager.startWatching(getUserData().getStorageRefList().getEntries(), observer);
    }

    public void stopSyncing() {
        if (syncManager == null)
            return;
        syncManager.stopWatching();
        syncManager = null;
    }

    public void startCommandManagers(Task.IObserver<TaskUpdate, Void> outgoingCommandObserver) {
        outgoingQueueManager = new OutgoingQueueManager(userData.getOutgoingCommandQueue(), connectionManager);
        outgoingQueueManager.start(outgoingCommandObserver);

        incomingCommandManager = new IncomingCommandManager(userData);
        incomingCommandManager.start();

        contactRequestHandler.start();
    }

    public IncomingCommandManager getIncomingCommandManager() {
        return incomingCommandManager;
    }

    public void grantAccess(String branch, int rights, ContactPublic contact) throws CryptoException, JSONException,
            IOException {
        AccessRight accessRight = new AccessRight(branch);
        accessRight.setGitAccessRights(rights);

        JSONArray accessRights = new JSONArray();
        accessRights.put(accessRight.toJson());

        AccessToken accessToken = AccessToken.create(context);
        accessToken.setAccessEntry(accessRights.toString());

        userData.getAccessStore().addAccessToken(accessToken);

        // send command to contact
        AccessCommand accessCommand = new AccessCommand(context, userData.getIdentityStore().getMyself(), contact,
                accessToken);
        userData.getAccessStore().commit();
        userData.getOutgoingCommandQueue().post(accessCommand, contact.getRemotes().getDefault(), true);
    }

    public void peekRemoteStatus(String branchId, Task.IObserver<Void, WatchJob.Result> observer) {
        Storage branch = getUserData().getStorageRefList().get(branchId);
        Remote remote = getUserData().getRemoteList().getDefault();
        connectionManager.submit(new WatchJob(context, remote.getUser(), Collections.singletonList(branch), true),
                new ConnectionManager.ConnectionInfo(remote.getUser(), remote.getServer()),
                new ConnectionManager.AuthInfo(ConnectionManager.AuthInfo.NONE, null),
                observer);
    }
}
