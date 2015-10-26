/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;


import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.transport.RefSpec;

import java.io.IOException;
import java.util.*;

public class GitSyncJob extends JsonRemoteJob {
    static final public String METHOD = "gitPull";
    static final public String METHOD_REQUEST_ADVERTISEMENT = "getAdvertisement";
    static final public String METHOD_REQUEST_PULL_DATA = "pullData";
    static final public String SERVER_USER_KEY = "serverUser";
    static final public String BRANCH_KEY = "branch";

    final private Repository repository;
    final private String serverUser;
    final private String branch;

    public GitSyncJob(Repository repository, String serverUser, String branch) {
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

        String advertisementHeader = jsonRPC.call(GitSyncJob.METHOD, new JsonRPC.Argument("request",
                METHOD_REQUEST_ADVERTISEMENT), serverUserArg, branchArg);
        startNewJsonRPC();
        String header = jsonRPC.call(GitSyncJob.METHOD, new JsonRPC.Argument("request",
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

    private Result mergeAndPush(String refName, ObjectId ours, ObjectId theirs) throws IOException {
        Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(repository);
        boolean merged = ourMerger.merge(ours, theirs);
        if (!merged)
            new Result(Result.ERROR, "failed to merge branches: ours=" + ours.name() + " theirs=" + theirs.name());
        else {
            ObjectId mergedTree = ourMerger.getResultTreeId();

            CommitBuilder commit = new CommitBuilder();
            PersonIdent personIdent = new PersonIdent("PackManager", "");
            commit.setCommitter(personIdent);
            commit.setAuthor(personIdent);
            commit.setMessage("merge");
            commit.setTreeId(mergedTree);
            commit.addParentId(ours);
            commit.addParentId(theirs);

            ObjectInserter objectInserter = repository.newObjectInserter();
            ObjectId commitId = objectInserter.insert(commit);
            objectInserter.flush();

            RefUpdate refUpdate = repository.updateRef(refName);
            //refUpdate.setForceUpdate(true);
            refUpdate.setNewObjectId(commitId);
            refUpdate.setExpectedOldObjectId(ours);
            refUpdate.setRefLogMessage("auto merge", false);
            RefUpdate.Result result = refUpdate.update();
            if (result != RefUpdate.Result.FAST_FORWARD)
                return new Result(Result.ERROR, "failed to merge");
            setFollowUpJob(new GitPushJob(repository, serverUser, branch));
        }
        return new Result(Result.FOLLOW_UP_JOB, "branch merged");
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

        ObjectId newRef = fetch(refName, remoteRequest);
        if (newRef == null) {
            setFollowUpJob(new GitPushJob(repository, serverUser, branch));
            return new Result(Result.FOLLOW_UP_JOB, "remote does not exist, try to push");
        }

        RefUpdate refUpdate = repository.updateRef(refName);
        //refUpdate.setForceUpdate(true);
        refUpdate.setNewObjectId(newRef);
        refUpdate.setExpectedOldObjectId(oldTip);
        refUpdate.setRefLogMessage("pull", false);
        RefUpdate.Result result = refUpdate.update();
        switch (result) {
            case NOT_ATTEMPTED:
            case LOCK_FAILURE:
            case FORCED:
            case IO_FAILURE:
            case RENAMED:
                throw new IOException("can't update tip");
            case NO_CHANGE:
                return new Result(Result.DONE, "no changes");
            case NEW:
                return new Result(Result.DONE, "pulled new branch");
            case REJECTED:
            case REJECTED_CURRENT_BRANCH:
                return mergeAndPush(refName, oldTip, newRef);
            default: FAST_FORWARD:
                return new Result(Result.DONE, "sync ok");
        }
    }
}
