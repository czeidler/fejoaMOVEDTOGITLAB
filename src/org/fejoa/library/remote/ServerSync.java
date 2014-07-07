/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.eclipse.jgit.util.Base64;
import org.fejoa.library.database.IDatabaseInterface;
import org.fejoa.library.support.InStanzaHandler;
import org.fejoa.library.support.IqInStanzaHandler;
import org.fejoa.library.support.ProtocolInStream;
import org.fejoa.library.support.ProtocolOutStream;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;


public class ServerSync {
    final private RemoteStorageLink remoteStorageLink;
    private String remoteTip;

    public ServerSync(RemoteStorageLink remoteStorageLink) {
        this.remoteStorageLink = remoteStorageLink;
    }

    public Observable<Boolean> send() {
        return Observable.create(new Observable.OnSubscribeFunc<Boolean>() {
            @Override
            public Subscription onSubscribe(final Observer<? super Boolean> observer) {
                boolean syncDone;
                RemoteConnection remoteConnection = remoteStorageLink.getRemoteConnection();
                IRemoteRequest remoteRequest = remoteConnection.getRemoteRequest();
                try {
                    if (!remoteConnection.requestAuthenticationSync(remoteStorageLink.getMyself(),
                            remoteStorageLink.getServerUser()))
                        throw new IOException("can't authenticate");
                    syncDone = pull(remoteRequest);
                    if (syncDone)
                        syncDone = push(remoteRequest);
                    observer.onNext(syncDone);
                } catch (Exception e) {
                    observer.onError(e);
                }
                observer.onCompleted();

                return Subscriptions.empty();
            }
        });
    }

    class SyncPullData {
        public String branch;
        public String tip;
        public byte pack[];
    };

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

    };

    class SyncPullHandler extends InStanzaHandler {
        private SyncPullData data;

        public SyncPullHandler(SyncPullData syncPullData) {
            super("sync_pull", false);

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
    };

    private boolean pull(IRemoteRequest remoteRequest) throws ParserConfigurationException, TransformerException,
            IOException {
        IDatabaseInterface database = remoteStorageLink.getDatabaseInterface();
        String localBranch = database.getBranch();
        String lastSyncCommit = database.getLastSyncCommit(remoteStorageLink.getUid(), localBranch);

        ProtocolOutStream outStream = new ProtocolOutStream();
        Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_GET);
        outStream.addElement(iqStanza);
        Element syncStanza =  outStream.createElement("sync_pull");
        syncStanza.setAttribute("branch", localBranch);
        syncStanza.setAttribute("base", lastSyncCommit);
        iqStanza.appendChild(syncStanza);

        byte reply[] = RemoteConnection.send(remoteRequest, outStream);


        IqInStanzaHandler iqHandler = new IqInStanzaHandler(ProtocolOutStream.IQ_RESULT);
        SyncPullData syncPullData = new SyncPullData();
        SyncPullHandler syncPullHandler = new SyncPullHandler(syncPullData);
        iqHandler.addChildHandler(syncPullHandler);

        ProtocolInStream inStream = new ProtocolInStream(reply);
        inStream.addHandler(iqHandler);
        inStream.parse();

        String localTipCommit = database.getTip();

        if (syncPullHandler.hasBeenHandled() || !syncPullData.branch.equals(localBranch))
            throw new IOException("invalid server response");

        if (syncPullData.tip.equals(localTipCommit)) {
            return true;
        }
        // see if the server is ahead by checking if we got packages
        if (syncPullData.pack != null) {
            remoteTip = syncPullData.tip;

            database.importPack(syncPullData.pack, lastSyncCommit, syncPullData.tip, -1);

            localTipCommit = database.getTip();
            // done? otherwise it was a merge and we have to push our merge
            if (localTipCommit.equals(lastSyncCommit)) {
                return true;
            }
        }

        return false;
    }

    private boolean push(IRemoteRequest remoteRequest) throws Exception {
        IDatabaseInterface database = remoteStorageLink.getDatabaseInterface();
        String localBranch = database.getBranch();
        String lastSyncCommit = database.getLastSyncCommit(remoteStorageLink.getUid(), localBranch);
        String localTipCommit = database.getTip();

        // we are ahead of the server: push changes to the server
        byte pack[] = database.exportPack(lastSyncCommit, localTipCommit, remoteTip, -1);

        ProtocolOutStream outStream = new ProtocolOutStream();
        Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_SET);
        outStream.addElement(iqStanza);
        Element pushStanza = outStream.createElement("sync_push");
        pushStanza.setAttribute("branch", localBranch);
        pushStanza.setAttribute("start_commit", remoteTip);
        pushStanza.setAttribute("last_commit", localTipCommit);
        iqStanza.appendChild(pushStanza);

        Element pushPackStanza = outStream.createElement("pack");
        pushPackStanza.setTextContent(Base64.encodeBytes(pack));
        iqStanza.appendChild(pushPackStanza);

        byte reply[] = RemoteConnection.send(remoteRequest, outStream);

        // TODO check if it went through!
        database.updateLastSyncCommit(remoteStorageLink.getUid(), database.getBranch(), localTipCommit);

        return true;
    }
}
