/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.fejoa.library2.*;
import org.fejoa.server.Portal;
import org.json.JSONObject;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;


public class GitPushJob extends JsonRemoteJob<RemoteJob.Result> {
    static final public String METHOD = "gitPush";
    static final public String METHOD_REQUEST_ADVERTISEMENT = "getAdvertisement";
    static final public String METHOD_REQUEST_PUSH_DATA = "pushData";

    final private Repository repository;
    final private String serverUser;
    final private String branch;

    static public org.eclipse.jgit.lib.ProgressMonitor progressMonitor = new ProgressMonitor() {
        @Override
        public void start(int i) {
            //System.out.println("start " + i);
        }

        @Override
        public void beginTask(String s, int i) {
            //System.out.println("beginTask " + s + " " + i);
        }

        @Override
        public void update(int i) {
            //System.out.println("update " + i);
        }

        @Override
        public void endTask() {
            //System.out.println("endTask");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    public GitPushJob(Repository repository, String serverUser, String branch) {
        this.repository = repository;
        this.serverUser = serverUser;
        this.branch = branch;
    }

    @Override
    public Result run(IRemoteRequest remoteRequest) throws Exception {
        super.run(remoteRequest);

        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(org.fejoa.library2.Constants.SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(org.fejoa.library2.Constants.BRANCH_KEY, branch);

        String advertisementHeader = jsonRPC.call(GitPushJob.METHOD, new JsonRPC.Argument("request",
                METHOD_REQUEST_ADVERTISEMENT), serverUserArg, branchArg);

        // read advertisement
        remoteRequest.open(advertisementHeader, false);
        RemoteJob.Result result = getResult(getReturnValue(remoteRequest.receiveHeader()));
        if (result.status != Portal.Errors.DONE)
            return result;
        // start new json RPC after we received and verified the return message
        startNewJsonRPC();
        String header = jsonRPC.call(GitPushJob.METHOD, new JsonRPC.Argument("request", METHOD_REQUEST_PUSH_DATA),
                serverUserArg, branchArg);

        GitTransportFejoa transport = new GitTransportFejoa(repository, remoteRequest, header);
        GitTransportFejoa.SmartFejoaPushConnection connection
                = (GitTransportFejoa.SmartFejoaPushConnection)transport.openPush();
        connection.readAdvertisedRefs(remoteRequest.receiveData());
        remoteRequest.close();

        // push
        List<RefSpec> specs = new ArrayList<>();
        specs.add(new RefSpec("refs/heads/" + branch));
        Collection<RemoteRefUpdate> remoteRefUpdates = Transport.findRemoteRefUpdatesFor(repository, specs, null);
        HashMap<String, RemoteRefUpdate> toPush = new HashMap<>();
        for (final RemoteRefUpdate rru : remoteRefUpdates) {
            if (toPush.put(rru.getRemoteName(), rru) != null)
                throw new TransportException(MessageFormat.format(
                        JGitText.get().duplicateRemoteRefUpdateIsIllegal, rru.getRemoteName()));
        }

        try {
            connection.push(progressMonitor, toPush);
        } catch (Exception e) {
            result = getResult(getReturnValue(remoteRequest.receiveHeader()));
            if (result.status != Portal.Errors.DONE)
                return result;
            return new Result(Portal.Errors.EXCEPTION, e.getMessage());
        }

        return new Result(Portal.Errors.DONE, "ok");
    }
}

