/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.eclipse.jgit.util.Base64;
import org.fejoa.library.Contact;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.ContactPublic;
import org.fejoa.library.UserIdentity;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.mailbox.MessageBranch;
import org.fejoa.library.mailbox.MessageBranchInfo;
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library2.remote.JsonRPC;
import org.json.JSONObject;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;


public class PublishMessageBranch extends RemoteTask {
    private MessageChannel messageChannel;

    public PublishMessageBranch(ConnectionManager connectionManager, MessageChannel messageChannel) {
        super(connectionManager);
        this.messageChannel = messageChannel;
    }

    public Observable<RemoteConnectionJob.Result> publish() {
        MessageBranch messageBranch = messageChannel.getBranch();
        if (messageBranch == null) {
            // just try to pull the branch from our server
            ContactPrivate myself = messageChannel.getMailbox().getUserIdentity().getMyself();
            ConnectionInfo connectionInfo = new ConnectionInfo(myself.getServer(), myself.getServerUser(), myself);
            InitPublishMessageBranchJob initPublishMessageBranchJob = new InitPublishMessageBranchJob(messageChannel,
                    connectionInfo, myself);
            final RemoteConnection remoteConnection = connectionManager.getConnection(connectionInfo);
            return remoteConnection.queueJob(initPublishMessageBranchJob);
        }

        MessageBranchInfo info = messageBranch.getMessageBranchInfo();
        List<MessageBranchInfo.Participant> participantList = info.getParticipants();
        UserIdentity userIdentity = messageBranch.getIdentity();

        List<Observable<RemoteConnectionJob.Result>> observableList = new ArrayList<>();
        for (MessageBranchInfo.Participant participant : participantList) {
            ConnectionInfo connectionInfo;
            ContactRequest.JsonContactRequestJob contactRequestJob = null;
            Contact contactPublic = userIdentity.getContactFinder().find(participant.uid);
            if (contactPublic == null)
                contactPublic = userIdentity.getContactFinder().findByAddress(participant.address);
            if (participant.uid.equals("") || contactPublic == null) {
                //contact request
                String[] parts = participant.address.split("@");
                if (parts.length != 2)
                    continue;
                String server = parts[1];
                String serverUser = parts[0];

                connectionInfo = new ConnectionInfo(server, serverUser, userIdentity.getMyself());
                ContactRequest contactRequest = new ContactRequest(connectionInfo, messageBranch.getIdentity());

                contactRequestJob = contactRequest.getContactRequestJob();
            } else {
                connectionInfo = new ConnectionInfo(contactPublic.getServer(), contactPublic.getServerUser(),
                        userIdentity.getMyself());
            }

            InitPublishMessageBranchJob initPublishMessageBranchJob = new InitPublishMessageBranchJob(messageChannel,
                    connectionInfo, contactPublic);
            final RemoteConnection remoteConnection = connectionManager.getConnection(connectionInfo);
            if (contactRequestJob != null) {
                // set listener so that the initPublishMessageBranchJob knows where to send the branch
                contactRequestJob.setListener(initPublishMessageBranchJob.getContactListener());
                contactRequestJob.setFollowUpJob(initPublishMessageBranchJob);
                observableList.add(remoteConnection.queueJobNoLogin(contactRequestJob));
            } else
                observableList.add(remoteConnection.queueJob(initPublishMessageBranchJob));
        }

        return Observable.merge(Observable.from(observableList));
    }

    class InitPublishMessageBranchJob extends JsonRemoteConnectionJob {
        final private MessageChannel messageChannel;
        final private ConnectionInfo connectionInfo;
        private Contact receiver;

        public InitPublishMessageBranchJob(MessageChannel messageChannel, ConnectionInfo connectionInfo,
                                           Contact receiver) {
            this.messageChannel = messageChannel;
            this.connectionInfo = connectionInfo;
            this.receiver = receiver;
        }

        @Override
        public byte[] getRequest() throws Exception {
            return jsonRPC.call("initPublishBranch",
                    new JsonRPC.Argument("serverUser", connectionInfo.serverUser),
                    new JsonRPC.Argument("branch", messageChannel.getBranchName()))
                    .getBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            JSONObject result = jsonRPC.getReturnValue(new String(reply));
            if (getStatus(result) != 0)
                return new Result(Result.ERROR, getMessage(result));

            if (receiver == null)
                return new Result(Result.ERROR, "internal error; no receiver");

            int transactionId = result.getInt("transactionId");
            String signatureAlgorithm = result.getString("signatureAlgorithm");
            byte[] signedToken = messageChannel.sign(result.getString("signToken"), signatureAlgorithm);
            boolean messageChannelNeeded = result.getBoolean("messageChannelNeeded");
            byte[] channelPack = null;
            String channelPEMKey = null;
            if (messageChannelNeeded) {
                channelPack = messageChannel.sharePack(connectionInfo.myself, connectionInfo.myself.getMainKeyId(),
                        receiver, receiver.getMainKeyId());
                channelPEMKey = messageChannel.shareSignatureKeyPEM();
            }

            setFollowUpJob(new LoginPublishMessageBranchJob(messageChannel, connectionInfo, transactionId, signedToken,
                    channelPack, channelPEMKey));

            return new Result(Result.DONE, getMessage(result));
        }

        public ContactRequest.IListener getContactListener() {
            return new ContactRequest.IListener() {
                @Override
                public void onNewContact(ContactPublic contactPublic) {
                    receiver = contactPublic;
                }
            };
        }
    }

    class LoginPublishMessageBranchJob extends JsonRemoteConnectionJob {
        final private MessageChannel messageChannel;
        final private ConnectionInfo connectionInfo;
        final private int transactionId;
        final private byte[] signedToken;
        final private byte[] channelPack;
        final private String channelPEMKey;

        public LoginPublishMessageBranchJob(MessageChannel messageChannel, ConnectionInfo connectionInfo,
                                            int transactionId, byte[] signedToken, byte[] channelPack,
                                            String channelPEMKey) {
            this.messageChannel = messageChannel;

            this.connectionInfo = connectionInfo;
            this.transactionId = transactionId;
            this.signedToken = signedToken;
            this.channelPack = channelPack;
            this.channelPEMKey = channelPEMKey;
        }

        @Override
        public byte[] getRequest() throws Exception {
            List<JsonRPC.Argument> arguments = new ArrayList<>();
            arguments.add(new JsonRPC.Argument("transactionId", transactionId));
            arguments.add(new JsonRPC.Argument("signedToken", (signedToken == null) ? "" : Base64.encodeBytes(signedToken)));
            arguments.add(new JsonRPC.Argument("branch", messageChannel.getBranchName()));
            if (channelPack != null && channelPEMKey != null) {
                arguments.add(new JsonRPC.Argument("channelHeader", Base64.encodeBytes(channelPack)));
                arguments.add(new JsonRPC.Argument("channelSignatureKey", channelPEMKey));
            }
            return jsonRPC.call("loginPublishBranch", arguments.toArray(new JsonRPC.Argument[arguments.size()]))
                    .getBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            JSONObject result = jsonRPC.getReturnValue(new String(reply));
            if (getStatus(result) != 0)
                return new Result(Result.ERROR, getMessage(result));

            String localTip = "";
            MessageBranch messageBranch = messageChannel.getBranch();
            if (messageBranch != null)
                localTip = messageBranch.getMessageStorage().getTip();
            String remoteTip = result.getString("remoteTip");
            if (localTip.equals(remoteTip)) {
                String branchName = messageChannel.getBranchName();
                return new Result(Result.DONE, new SyncResultData(connectionInfo.getRemoteId(), branchName, localTip),
                        "branch in sync");
            }

            StorageDir messageStorage = messageChannel.getMessageStorage();
            setFollowUpJob(new JsonSync(messageStorage, connectionInfo.serverUser, connectionInfo.getRemoteId()));

            return new Result(Result.DONE, getMessage(result));
        }
    }
}
