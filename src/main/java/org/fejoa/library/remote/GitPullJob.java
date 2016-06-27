/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.RefSpec;
import org.fejoa.server.Portal;
import org.json.JSONException;

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
    private ObjectId fetchedHead;

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
    private RemoteJob.Result fetch(String refName, IRemoteRequest remoteRequest) throws IOException, JSONException {
        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(BRANCH_KEY, branch);

        String advertisementHeader = jsonRPC.call(METHOD, new JsonRPC.Argument("request",
                METHOD_REQUEST_ADVERTISEMENT), serverUserArg, branchArg);

        // read advertisement
        remoteRequest.open(advertisementHeader, false);
        RemoteJob.Result result = getResult(getReturnValue(remoteRequest.receiveHeader()));
        if (result.status != Portal.Errors.DONE)
            return result;
        // start new json RPC after we received and verified the return message
        startNewJsonRPC();
        String header = jsonRPC.call(METHOD, new JsonRPC.Argument("request",
                METHOD_REQUEST_PULL_DATA), serverUserArg, branchArg);

        GitTransportFejoa transport = new GitTransportFejoa(repository, remoteRequest, header);
        GitTransportFejoa.SmartHttpFetchConnection connection
                = (GitTransportFejoa.SmartHttpFetchConnection)transport.openFetch();
        connection.readAdvertisedRefs(remoteRequest.receiveData());
        remoteRequest.close();

        // pull
        RefSpec refSpec = new RefSpec(refName);
        List<Ref> want = new ArrayList<>();
        Ref remoteRef = connection.getRef(refSpec.getSource());
        if (remoteRef == null)
            return result;
        want.add(remoteRef);
        Set<ObjectId> have = new HashSet<>();
        try {
            connection.fetch(GitPushJob.progressMonitor, want, have);
        } catch (Exception e) {
            result = getResult(getReturnValue(remoteRequest.receiveHeader()));
            if (result.status != Portal.Errors.DONE)
                return result;
        }

        fetchedHead = want.get(0).getObjectId();
        return result;
    }

    @Override
    public Result run(IRemoteRequest remoteRequest) throws Exception {
        super.run(remoteRequest);

        final String refName = "refs/heads/" + branch;
        RemoteJob.Result result = fetch(refName, remoteRequest);
        if (result.status != Portal.Errors.DONE)
            return new Result(result.status, result.message, "");

        if (fetchedHead == null)
            return new Result(Portal.Errors.DONE, "remote does not exist", "");

        return new Result(Portal.Errors.DONE, "remote head: " + fetchedHead.getName(), fetchedHead.getName());
    }
}
