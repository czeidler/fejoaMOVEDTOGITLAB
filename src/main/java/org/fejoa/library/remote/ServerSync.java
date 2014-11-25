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
import org.fejoa.library.support.*;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import rx.Observable;

import java.io.IOException;

/**
 * Starts with a pull and add a push if necessary
 */
class Sync extends RemoteConnectionJob {
    final private StorageDir database;
    final private String serverUser;
    final private String remoteUid;

    public Sync(StorageDir database, String serverUser, String remoteUid) {
        this.database = database;
        this.serverUser = serverUser;
        this.remoteUid = remoteUid;
    }

    @Override
    public byte[] getRequest() throws Exception {
        String localBranch = database.getBranch();
        String lastSyncCommit = database.getLastSyncCommit(remoteUid, localBranch);

        ProtocolOutStream outStream = new ProtocolOutStream();
        Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_GET);
        outStream.addElement(iqStanza);
        Element syncStanza =  outStream.createElement("syncPull");
        syncStanza.setAttribute("serverUser", serverUser);
        syncStanza.setAttribute("branch", localBranch);
        syncStanza.setAttribute("base", lastSyncCommit);
        iqStanza.appendChild(syncStanza);

        return outStream.toBytes();
    }

    @Override
    public Result handleResponse(byte[] reply) throws Exception {
        IqInStanzaHandler iqHandler = new IqInStanzaHandler(ProtocolOutStream.IQ_RESULT);
        SyncPullData syncPullData = new SyncPullData();
        SyncPullHandler syncPullHandler = new SyncPullHandler(syncPullData);
        iqHandler.addChildHandler(syncPullHandler);

        ProtocolInStream inStream = new ProtocolInStream(reply);
        inStream.addHandler(iqHandler);
        inStream.parse();

        String localBranch = database.getBranch();
        String lastSyncCommit = database.getLastSyncCommit(remoteUid, localBranch);

        String localTipCommit = database.getTip();

        if (!syncPullHandler.hasBeenHandled() || !syncPullData.branch.equals(localBranch))
            throw new IOException("invalid server response");

        String remoteTip = syncPullData.tip;
        if (remoteTip.equals(localTipCommit)) {
            return new Result(Result.DONE, new SyncResultData(remoteUid, database.getBranch(), localTipCommit),
                    "was synced");
        }

        // see if the server is ahead by checking if we got packages
        if (syncPullData.pack != null && syncPullData.pack.length > 0) {


            database.importPack(syncPullData.pack, lastSyncCommit, syncPullData.tip, -1);

            localTipCommit = database.getTip();
            // already in sync? otherwise it was a merge and we have to push our merge
            if (localTipCommit.equals(remoteTip)) {
                database.updateLastSyncCommit(remoteUid, database.getBranch(), localTipCommit);

                return new Result(Result.DONE, new SyncResultData(remoteUid, database.getBranch(), localTipCommit),
                        "synced after pull");
            }
        }

        setFollowUpJob(new Push(database, serverUser, remoteUid, remoteTip));
        return new Result(Result.DONE);
    }
}

class Push extends RemoteConnectionJob {
    final private StorageDir database;
    final private String serverUser;
    final private String remoteUid;
    final private String remoteTip;

    public Push(StorageDir database, String serverUser, String remoteUid, String remoteTip) {
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

        ProtocolOutStream outStream = new ProtocolOutStream();
        Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_SET);
        outStream.addElement(iqStanza);
        Element pushStanza = outStream.createElement("syncPush");
        pushStanza.setAttribute("serverUser", serverUser);
        pushStanza.setAttribute("branch", localBranch);
        pushStanza.setAttribute("startCommit", remoteTip);
        pushStanza.setAttribute("lastCommit", localTipCommit);
        iqStanza.appendChild(pushStanza);

        Element pushPackStanza = outStream.createElement("pack");
        pushPackStanza.setTextContent(Base64.encodeBytes(pack));
        pushStanza.appendChild(pushPackStanza);

        return outStream.toBytes();
    }

    @Override
    public Result handleResponse(byte[] reply) throws Exception {
        // TODO check if it went through!
        IqInStanzaHandler resultHandler = new IqInStanzaHandler(ProtocolOutStream.IQ_RESULT);
        ProtocolInStream inStream = new ProtocolInStream(reply);
        inStream.addHandler(resultHandler);
        inStream.parse();

        if (!resultHandler.hasBeenHandled())
            return new Result(Result.ERROR);

        String localTipCommit = database.getTip();
        database.updateLastSyncCommit(remoteUid, database.getBranch(), localTipCommit);

        return new Result(Result.DONE, new SyncResultData(remoteUid, database.getBranch(), localTipCommit),
                "synced after push");
    }
}

class SyncPullData {
    public String branch;
    public String tip;
    public byte pack[];
}

class SyncPullPackHandler extends InStanzaHandler {
    private SyncPullData data;

    public SyncPullPackHandler(SyncPullData data) {
        super("pack", true);
        this.data = data;
    }

    @Override
    public boolean handleStanza(Attributes attributes)
    {
        return true;
    }

    @Override
    public boolean handleText(String text)
    {
        data.pack = Base64.decode(text);
        return true;
    }

}

class SyncPullHandler extends InStanzaHandler {
    private SyncPullData data;

    public SyncPullHandler(SyncPullData syncPullData) {
        super("syncPull", false);

        this.data = syncPullData;
        SyncPullPackHandler syncPullPackHandler = new SyncPullPackHandler(data);
        addChildHandler(syncPullPackHandler);
    }

    @Override
    public boolean handleStanza(Attributes attributes)
    {
        if (attributes.getIndex("branch") < 0)
            return false;
        if (attributes.getIndex("tip") < 0)
            return false;

        data.branch = attributes.getValue("branch");
        data.tip = attributes.getValue("tip");
        return true;
    }
}

public class ServerSync {
    final private RemoteStorageLink remoteStorageLink;

    public ServerSync(RemoteStorageLink remoteStorageLink) {
        this.remoteStorageLink = remoteStorageLink;
    }

    public Observable<RemoteConnectionJob.Result> sync() {
        final Sync sync = new Sync(remoteStorageLink.getLocalStorage(),
                remoteStorageLink.getConnectionInfo().serverUser, remoteStorageLink.getUid());

        final RemoteConnection remoteConnection = ConnectionManager.get().getConnection(
                remoteStorageLink.getConnectionInfo());

        return remoteConnection.queueJob(sync);
    }
}
