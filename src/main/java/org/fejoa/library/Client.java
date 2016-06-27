/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.command.*;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.*;
import org.fejoa.library.support.Task;
import org.json.JSONException;

import java.io.IOException;
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
        this.connectionManager = new ConnectionManager();
    }

    public void create(String userName, String server, String password) throws IOException, CryptoException {
        context.registerRootPassword(userName, server, password);
        userData = UserData.create(context, password);
        Remote remoteRemote = new Remote(userName, server);
        userData.getRemoteList().add(remoteRemote);
        userData.getRemoteList().setDefault(remoteRemote);
    }

    public void open(String password) throws IOException, CryptoException {
        userData = UserData.open(context, password);

        Remote defaultRemote = userData.getRemoteList().getDefault();
        context.registerRootPassword(defaultRemote.getUser(), defaultRemote.getServer(), password);
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
                new ConnectionManager.AuthInfo(),
                observer);
    }

    public void createAccount(String userName, String password, String userDataId, String server,
                              Task.IObserver<Void, RemoteJob.Result> observer) {
        connectionManager.submit(new CreateAccountJob(userName, password, userDataId,
                        CryptoSettings.getDefault().masterPassword),
                new ConnectionManager.ConnectionInfo(userName, server),
                new ConnectionManager.AuthInfo(),
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
        BranchAccessRight accessRight = new BranchAccessRight(BranchAccessRight.CONTACT_ACCESS);
        accessRight.addBranchAccess(branch, rights);

        AccessToken accessToken = AccessToken.create(context);
        accessToken.setAccessEntry(accessRight.toJson().toString());

        userData.getAccessStore().addAccessToken(accessToken);

        // send command to contact
        AccessCommand accessCommand = new AccessCommand(context, userData.getIdentityStore().getMyself(), contact,
                accessToken);
        userData.getAccessStore().commit();
        userData.getOutgoingCommandQueue().post(accessCommand, contact.getRemotes().getDefault(), true);
    }

    // Requires to be root user. TODO: implement peek for contact branches?
    public void peekRemoteStatus(String branchId, Task.IObserver<Void, WatchJob.Result> observer) {
        Storage branch = getUserData().getStorageRefList().get(branchId);
        Remote remote = getUserData().getRemoteList().getDefault();
        connectionManager.submit(new WatchJob(context, remote.getUser(), Collections.singletonList(branch), true),
                new ConnectionManager.ConnectionInfo(remote.getUser(), remote.getServer()),
                context.getRootAuthInfo(remote),
                observer);
    }

    public void pullContactBranch(String user, String server, AccessTokenContact accessTokenContact,
                                  BranchAccessRight.Entry entry, final Task.IObserver<Void, GitPullJob.Result> observer)
            throws IOException {
        if ((entry.getRights() & BranchAccessRight.PULL) == 0)
            throw new IOException("missing rights!");

        final StorageDir contactBranch = getContext().getStorage(entry.getBranch());

        getConnectionManager().submit(new GitPullJob(((JGitInterface) contactBranch.getDatabase()).getRepository(),
                        user, entry.getBranch()),
                new ConnectionManager.ConnectionInfo(user, server),
                new ConnectionManager.AuthInfo(user, accessTokenContact),
                new Task.IObserver<Void, GitPullJob.Result>() {
                    @Override
                    public void onProgress(Void update) {
                        observer.onProgress(update);
                    }

                    @Override
                    public void onResult(GitPullJob.Result result) {
                        try {
                            contactBranch.merge(result.pulledRev);
                        } catch (IOException exception) {
                            observer.onException(exception);
                        }
                        observer.onResult(result);
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                    }
                });
    }

    public void migrate(String newUserName, String newServer) {

    }
}
