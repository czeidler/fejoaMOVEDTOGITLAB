/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;


public class GitPushJob extends JsonRemoteJob {
    static final public String METHOD = "gitPush";
    static final public String METHOD_REQUEST_ADVERTISEMENT = "getAdvertisement";
    static final public String METHOD_REQUEST_PUSH_DATA = "pushData";
    static final public String SERVER_USER_KEY = "serverUser";
    static final public String BRANCH_KEY = "branch";


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
    public Result run(IRemoteRequest remoteRequest) throws IOException {
        super.run(remoteRequest);

        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(BRANCH_KEY, branch);

        String advertisementHeader = jsonRPC.call(GitPushJob.METHOD, new JsonRPC.Argument("request",
                METHOD_REQUEST_ADVERTISEMENT), serverUserArg, branchArg);
        startNewJsonRPC();
        String header = jsonRPC.call(GitPushJob.METHOD, new JsonRPC.Argument("request", METHOD_REQUEST_PUSH_DATA),
                serverUserArg, branchArg);

        GitTransportFejoa transport = new GitTransportFejoa(repository, remoteRequest, header);
        GitTransportFejoa.SmartFejoaPushConnection connection
                = (GitTransportFejoa.SmartFejoaPushConnection)transport.openPush();

        // read advertisement
        remoteRequest.open(advertisementHeader, false);
        connection.readAdvertisedRefs(remoteRequest.receiveData());
        remoteRequest.close();

        // push
        List<RefSpec> specs = new ArrayList<>();
        specs.add(new RefSpec("refs/heads/" + branch));
        Collection<RemoteRefUpdate> remoteRefUpdates = Transport.findRemoteRefUpdatesFor(repository, specs, null);
        HashMap<String, RemoteRefUpdate> toPush = new HashMap<String, RemoteRefUpdate>();
        for (final RemoteRefUpdate rru : remoteRefUpdates) {
            if (toPush.put(rru.getRemoteName(), rru) != null)
                throw new TransportException(MessageFormat.format(
                        JGitText.get().duplicateRemoteRefUpdateIsIllegal, rru.getRemoteName()));
        }

        connection.push(progressMonitor, toPush);

        return new Result(Result.DONE, "ok");
    }
}

