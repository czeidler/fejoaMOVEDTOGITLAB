/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.database.JGitInterface;
import org.fejoa.library2.AccessTokenContact;
import org.fejoa.library2.Constants;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.database.StorageDir;
import org.fejoa.library2.remote.*;
import org.fejoa.library2.util.Task;
import org.json.JSONObject;

import java.io.InputStream;


public class RemotePullHandler extends JsonRequestHandler {
    public RemotePullHandler() {
        super(RemotePullJob.METHOD);
    }

    @Override
    public void handle(final Portal.ResponseHandler responseHandler, final JsonRPCHandler jsonRPCHandler,
                       InputStream data, Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String serverUser = params.getString(Constants.SERVER_USER_KEY);
        AccessControl accessControl = new AccessControl(session, serverUser);
        if (!accessControl.isRootUser()) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.ACCESS_DENIED,
                    "Only root users can do a remote pull."));
            return;
        }

        FejoaContext context = session.getContext(serverUser);
        AccessTokenContact accessTokenContact = new AccessTokenContact(context, params.getString(
                RemotePullJob.ACCESS_TOKEN_KEY));
        String branch = params.getString(Constants.BRANCH_KEY);
        String sourceUser = params.getString(RemotePullJob.SOURCE_USER_KEY);
        String sourceServer = params.getString(RemotePullJob.SOURCE_SERVER_KEY);

        StorageDir targetDir = context.getStorage(branch);
        GitPullJob pullJob = new GitPullJob(((JGitInterface)targetDir.getDatabase()).getRepository(), sourceUser,
                branch);

        ConnectionManager connectionManager = new ConnectionManager();
        connectionManager.setStartScheduler(new Task.CurrentThreadScheduler());
        connectionManager.setObserverScheduler(new Task.CurrentThreadScheduler());
        connectionManager.submit(pullJob, new ConnectionManager.ConnectionInfo(sourceUser, sourceServer),
                new ConnectionManager.AuthInfo(sourceUser, accessTokenContact),
                new Task.IObserver<Void, GitPullJob.Result>() {
                    @Override
                    public void onProgress(Void aVoid) {

                    }

                    @Override
                    public void onResult(GitPullJob.Result result) {
                        responseHandler.setResponseHeader(jsonRPCHandler.makeResult(result.status,
                                    result.message));
                    }

                    @Override
                    public void onException(Exception exception) {
                        responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.EXCEPTION,
                                exception.getMessage()));
                    }
                });
    }
}
