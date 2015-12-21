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
import org.fejoa.library2.command.IncomingCommandManager;
import org.fejoa.library2.command.OutgoingQueueManager;
import org.fejoa.library2.remote.*;

import java.io.IOException;

public class Client {
    final private FejoaContext context;
    private ConnectionManager connectionManager;
    private UserData userData;
    private SyncManager syncManager;
    private OutgoingQueueManager outgoingQueueManager;
    private IncomingCommandManager incomingCommandManager;

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

        incomingCommandManager = new IncomingCommandManager(context, userData);
        incomingCommandManager.start();
    }

    public IncomingCommandManager getIncomingCommandManager() {
        return incomingCommandManager;
    }
}
