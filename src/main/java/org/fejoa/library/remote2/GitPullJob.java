/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;


import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;

import java.io.IOException;
import java.util.*;

public class GitPullJob extends JsonRemoteJob {
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

    @Override
    public Result run(IRemoteRequest remoteRequest) throws IOException {
        super.run(remoteRequest);

        final String refName = "refs/heads/" + branch;
        Ref tip = repository.getRef(refName);
        ObjectId oldTip;
        if (tip == null)
            oldTip = ObjectId.zeroId();
        else
            oldTip = tip.getObjectId();

        JsonRPC jsonRPC = new JsonRPC();
        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(BRANCH_KEY, branch);

        String advertisementHeader = jsonRPC.call(GitPullJob.METHOD, new JsonRPC.Argument("request",
                METHOD_REQUEST_ADVERTISEMENT), serverUserArg, branchArg);
        String header = jsonRPC.call(GitPullJob.METHOD, new JsonRPC.Argument("request",
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
        want.add(connection.getRef(refSpec.getSource()));
        Set<ObjectId> have = new HashSet<>();
        connection.fetch(GitPushJob.progressMonitor, want, have);

        RefUpdate refUpdate = repository.updateRef(refName);
        refUpdate.setForceUpdate(true);
        refUpdate.setNewObjectId(want.get(0).getObjectId());
        refUpdate.setExpectedOldObjectId(oldTip);
        refUpdate.setRefLogMessage("pull", false);
        RefUpdate.Result result = refUpdate.update();
        switch (result) {
            case NOT_ATTEMPTED:
            case LOCK_FAILURE:
            case FORCED:
            case REJECTED:
            case REJECTED_CURRENT_BRANCH:
            case IO_FAILURE:
            case RENAMED:
                throw new IOException("can't update tip");
            case NO_CHANGE:
                return new Result(Result.DONE, "no changes");
            case NEW:
                return new Result(Result.DONE, "new");
            case FAST_FORWARD:
                break;
        }
        return new Result(Result.DONE, "ok");
    }
}
