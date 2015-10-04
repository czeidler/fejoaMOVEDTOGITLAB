/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.remote2.JsonRPC;
import org.fejoa.library.support.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import rx.Observable;
import rx.Observer;

import java.util.ArrayList;
import java.util.List;


class WatchHandler extends InStanzaHandler {
    public String status;

    public WatchHandler() {
        super(ServerWatcher.WATCH_BRANCHES_STANZA, false);

    }

    @Override
    public boolean handleStanza(Attributes attributes)
    {
        if (attributes.getIndex("status") < 0)
            return false;
        status = attributes.getValue("status");
        return true;
    }
};


class WatchItemsChangedHandler extends InStanzaHandler {
    public List<String> branches = new ArrayList<>();

    public WatchItemsChangedHandler() {
        super("branch", true);

    }

    @Override
    public boolean handleStanza(Attributes attributes) {
        if (attributes.getIndex("branch") < 0)
            return false;

        branches.add(attributes.getValue("branch"));
        return true;
    }
};


public class ServerWatcher extends RemoteTask implements RequestQueue.IIdleJob {
    public interface IListener {
        void onBranchesUpdated(List<RemoteStorageLink> links);
        void onError(String message);
        void onResult(RemoteConnectionJob.Result args);
    }

    public final static String WATCH_BRANCHES_STANZA = "watchBranches";

    private List<RemoteStorageLink> remoteStorageLinkList = new ArrayList<>();
    private IListener listener;

    private Observer<RemoteConnectionJob.Result> observer = new Observer<RemoteConnectionJob.Result>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            if (listener != null)
                listener.onError(e.getMessage());
        }

        @Override
        public void onNext(RemoteConnectionJob.Result args) {
            if (listener != null)
                listener.onResult(args);
        }
    };

    public ServerWatcher(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    public void setListener(IListener listener) {
        this.listener = listener;
    }

    public void setLinks(List<RemoteStorageLink> links) {
        remoteStorageLinkList = links;
        for (RemoteStorageLink link : links) {
            // The identities need to be synced before the mailboxes, e.g., it could happen that a new
            // contact sent a message, in case the identities are not synced first loading the message will
            // fail.
            if (link.getLocalStorage().getBranch().equals("identities")) {
                links.remove(link);
                remoteStorageLinkList.add(0, link);
                break;
            }
        }
    }

    @Override
    public Observable<RemoteConnectionJob.Result> getObservable() {
        // TODO: at the moment we only support watching one user per server so we just take the first connection info
        ConnectionInfo connectionInfo = remoteStorageLinkList.get(0).getConnectionInfo();

        final RemoteConnection remoteConnection = connectionManager.getConnection(connectionInfo);
        return remoteConnection.runJob(new JsonWatch());
    }

    @Override
    public Observer<RemoteConnectionJob.Result> getObserver() {
        return observer;
    }

    private RemoteStorageLink findLink(String branch) {
        for (RemoteStorageLink link : remoteStorageLinkList) {
            if (link.getLocalStorage().getBranch().equals(branch))
                return link;
        }
        return null;
    }

    class JsonWatch extends JsonRemoteConnectionJob {
        @Override
        public byte[] getRequest() throws Exception {
            List<JsonRPC.ArgumentSet> branches = new ArrayList<>();
            for (RemoteStorageLink link : remoteStorageLinkList) {
                JsonRPC.ArgumentSet argumentSet = new JsonRPC.ArgumentSet(
                        new JsonRPC.Argument("branch", link.getLocalStorage().getBranch()),
                        new JsonRPC.Argument("tip", link.getLocalStorage().getTip())
                );
                branches.add(argumentSet);
            }

            return jsonRPC.call(WATCH_BRANCHES_STANZA, new JsonRPC.Argument("branches", branches)).getBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            JSONObject result = jsonRPC.getReturnValue(new String(reply));
            if (getStatus(result) != 0)
                return new Result(Result.ERROR, getMessage(result));

            if (!result.has("response"))
                return new Result(Result.ERROR, "bad response");

            String response = result.getString("response");
            if (response.equals("serverTimeout")) {
                return new Result(Result.DONE, "server timeout");
            } else if (response.equals("update")) {
                if (!result.has("branches"))
                    return new Result(Result.ERROR, "bad response, branches are missing");

                if (listener != null) {
                    List<RemoteStorageLink> links = new ArrayList<>();

                    JSONArray branches = result.getJSONArray("branches");
                    for (int i = 0; i < branches.length(); i++) {
                        String branch = branches.getString(i);
                        RemoteStorageLink link = findLink(branch);
                        if (link == null)
                            continue;
                        links.add(link);
                    }

                    listener.onBranchesUpdated(links);
                }
            }
            return new Result(Result.DONE);
        }
    }

    class Watch extends RemoteConnectionJob {
        @Override
        public byte[] getRequest() throws Exception {
            ProtocolOutStream outStream = new ProtocolOutStream();
            Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_GET);
            outStream.addElement(iqStanza);

            Element watchStanza =  outStream.createElement(WATCH_BRANCHES_STANZA);
            iqStanza.appendChild(watchStanza);

            for (RemoteStorageLink link : remoteStorageLinkList) {
                Element item =  outStream.createElement("branch");
                item.setAttribute("branch", link.getLocalStorage().getBranch());
                item.setAttribute("tip", link.getLocalStorage().getTip());
                watchStanza.appendChild(item);
            }

            return outStream.toBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            IqInStanzaHandler iqHandler = new IqInStanzaHandler(ProtocolOutStream.IQ_RESULT);
            WatchHandler watchHandler = new WatchHandler();
            WatchItemsChangedHandler itemsChangedHandler = new WatchItemsChangedHandler();
            iqHandler.addChildHandler(watchHandler);
            watchHandler.addChildHandler(itemsChangedHandler);

            ProtocolInStream inStream = new ProtocolInStream(reply);
            inStream.addHandler(iqHandler);
            inStream.parse();

            if (!watchHandler.hasBeenHandled())
                return new Result(Result.ERROR);

            if (watchHandler.status.equals("serverTimeout")) {
                return new Result(Result.DONE, watchHandler.status);
            } else if (watchHandler.status.equals("update")) {
                if (listener != null) {
                    List<RemoteStorageLink> links = new ArrayList<>();
                    for (String branch : itemsChangedHandler.branches) {
                        RemoteStorageLink link = findLink(branch);
                        if (link == null)
                            continue;
                        links.add(link);
                    }

                    listener.onBranchesUpdated(links);
                }
            }
            return new Result(Result.DONE);
        }
    }

}
