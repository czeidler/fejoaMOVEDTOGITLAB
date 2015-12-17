/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.RefSpec;

import java.io.IOException;
import java.util.*;


public class GitPullJob extends JsonRemoteJob<GitPullJob.Result> {
    public static class Result extends RemoteJob.Result {
        final public String pulledRev;

        public Result(int status, String message, String pulledRev) {
            super(status, message);

            this.pulledRev = pulledRev;
        }
    }

    static final public String METHOD = "gitPull";
    static final public String METHOD_REQUEST_ADVERTISEMENT = "getAdvertisement";
    static final public String METHOD_REQUEST_PULL_DATA = "pullData";
    static final public String SERVER_USER_KEY = "serverUser";
    static final public String BRANCH_KEY = "branch";

    final private Repository repository;
    final private String serverUser;
    final private String branch;

    public GitPullJob(Repository repository, String serverUser, String branch) {
        this.repository = repository;
        this.serverUser = serverUser;
        this.branch = branch;
    }

    /**
     * Fetches a branch.
     *
     * @param refName the branch ref
     * @param remoteRequest the remote request
     * @return object id of the fetched ref
     * @throws IOException
     */
    private ObjectId fetch(String refName, IRemoteRequest remoteRequest) throws IOException {
        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(BRANCH_KEY, branch);

        String advertisementHeader = jsonRPC.call(METHOD, new JsonRPC.Argument("request",
                METHOD_REQUEST_ADVERTISEMENT), serverUserArg, branchArg);
        startNewJsonRPC();
        String header = jsonRPC.call(METHOD, new JsonRPC.Argument("request",
                METHOD_REQUEST_PULL_DATA), serverUserArg, branchArg);

        GitTransportFejoa transport = new GitTransportFejoa(repository, remoteRequest, header);
        GitTransportFejoa.SmartHttpFetchConnection connection
                = (GitTransportFejoa.SmartHttpFetchConnection)transport.openFetch();

        // read advertisement
        remoteRequest.open(advertisementHeader, false);
        connection.readAdvertisedRefs(remoteRequest.receiveData());
        remoteRequest.close();

        // pull
        RefSpec refSpec = new RefSpec(refName);
        List<Ref> want = new ArrayList<>();
        Ref remoteRef = connection.getRef(refSpec.getSource());
        if (remoteRef == null)
            return null;
        want.add(remoteRef);
        Set<ObjectId> have = new HashSet<>();
        connection.fetch(GitPushJob.progressMonitor, want, have);

        return want.get(0).getObjectId();
    }

    @Override
    public Result run(IRemoteRequest remoteRequest) throws IOException {
        super.run(remoteRequest);

        final String refName = "refs/heads/" + branch;
        ObjectId newRef = fetch(refName, remoteRequest);
        if (newRef == null)
            return new Result(Result.DONE, "remote does not exist", "");

        return new Result(Result.DONE, "remote head: " + newRef.getName(), newRef.getName());
    }
}
