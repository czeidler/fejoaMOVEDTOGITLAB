/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.eclipse.jgit.util.Base64;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library2.remote.JsonRPC;
import org.json.JSONObject;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;


class JsonSync extends JsonRemoteConnectionJob {
    final private StorageDir database;
    final private String serverUser;
    final private String remoteUid;

    public JsonSync(StorageDir database, String serverUser, String remoteUid) {
        this.database = database;
        this.serverUser = serverUser;
        this.remoteUid = remoteUid;
    }

    @Override
    public byte[] getRequest() throws Exception {
        String localBranch = database.getBranch();
        String lastSyncCommit = database.getLastSyncCommit(remoteUid, localBranch);

        return jsonRPC.call("syncPull",
                new JsonRPC.Argument("serverUser", serverUser),
                new JsonRPC.Argument("branch", localBranch),
                new JsonRPC.Argument("base", lastSyncCommit))
                .getBytes();
    }

    @Override
    public Result handleResponse(byte[] reply) throws Exception {
        JSONObject result = jsonRPC.getReturnValue(new String(reply));
        if (getStatus(result) != 0)
            return new Result(Result.ERROR, getMessage(result));

        String remoteBranch = result.getString("branch");
        String remoteTip = result.getString("tip");
        byte[] pack = null;
        if (result.has("pack"))
            pack = Base64.decode(result.getString("pack"));

        String localBranch = database.getBranch();
        String lastSyncCommit = database.getLastSyncCommit(remoteUid, localBranch);
        String localTipCommit = database.getTip();

        if (!remoteBranch.equals(localBranch))
            return new Result(Result.ERROR, "sync branch mismatch");

        if (remoteTip.equals(localTipCommit)) {
            return new Result(Result.DONE, new SyncResultData(remoteUid, database.getBranch(), localTipCommit),
                    "was synced");
        }

        // see if the server is ahead by checking if we got packages
        if (pack != null && pack.length > 0) {
            database.importPack(pack, lastSyncCommit, remoteTip, -1);

            localTipCommit = database.getTip();
            // already in sync? otherwise it was a merge and we have to push our merge
            if (localTipCommit.equals(remoteTip)) {
                database.updateLastSyncCommit(remoteUid, database.getBranch(), localTipCommit);

                return new Result(Result.DONE, new SyncResultData(remoteUid, database.getBranch(), localTipCommit),
                        "synced after pull");
            }
        }

        setFollowUpJob(new JsonPush(database, serverUser, remoteUid, remoteTip));
        return new Result(Result.DONE);
    }
}


class JsonPush extends JsonRemoteConnectionJob {
    final private StorageDir database;
    final private String serverUser;
    final private String remoteUid;
    final private String remoteTip;

    public JsonPush(StorageDir database, String serverUser, String remoteUid, String remoteTip) {
        this.database = database;
        this.serverUser = serverUser;
        this.remoteUid = remoteUid;
        this.remoteTip = remoteTip;
    }

    @Override
    public byte[] getRequest() throws Exception {
        String localBranch = database.getBranch();
        String lastSyncCommit = database.getLastSyncCommit(remoteUid, localBranch);
        String localTipCommit = database.getTip();

        // the remote repository is gone upload it again TODO: that is only the obvious case
        if (remoteTip.equals(""))
            lastSyncCommit = "";
        // we are ahead of the server: push changes to the server
        byte pack[] = database.exportPack(lastSyncCommit, localTipCommit, remoteTip, -1);

        List<JsonRPC.Argument> arguments = new ArrayList<>();
        arguments.add(new JsonRPC.Argument("serverUser", serverUser));
        arguments.add(new JsonRPC.Argument("branch", localBranch));
        arguments.add(new JsonRPC.Argument("startCommit", remoteTip));
        arguments.add(new JsonRPC.Argument("lastCommit", localTipCommit));
        arguments.add(new JsonRPC.Argument("pack", Base64.encodeBytes(pack)));

        return jsonRPC.call("syncPush", arguments.toArray(new JsonRPC.Argument[arguments.size()])).getBytes();
    }

    @Override
    public Result handleResponse(byte[] reply) throws Exception {
        // TODO check if it went through!
        JSONObject result = jsonRPC.getReturnValue(new String(reply));
        if (getStatus(result) != 0)
            return new Result(Result.ERROR, getMessage(result));

        String localTipCommit = database.getTip();
        database.updateLastSyncCommit(remoteUid, database.getBranch(), localTipCommit);

        return new Result(Result.DONE, new SyncResultData(remoteUid, database.getBranch(), localTipCommit),
                "synced after push");
    }
}


public class ServerSync extends RemoteTask {
    final private RemoteStorageLink remoteStorageLink;

    public ServerSync(ConnectionManager connectionManager, RemoteStorageLink remoteStorageLink) {
        super(connectionManager);
        this.remoteStorageLink = remoteStorageLink;
    }

    public Observable<RemoteConnectionJob.Result> sync() {
        final JsonSync sync = new JsonSync(remoteStorageLink.getLocalStorage(),
                remoteStorageLink.getConnectionInfo().serverUser, remoteStorageLink.getUid());

        final RemoteConnection remoteConnection = connectionManager.getConnection(
                remoteStorageLink.getConnectionInfo());

        return remoteConnection.queueJob(sync);
    }
}
