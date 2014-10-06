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
import org.fejoa.library.ContactPublic;
import org.fejoa.library.UserIdentity;
import org.fejoa.library.mailbox.MessageBranchInfo;
import org.fejoa.library.mailbox.MessageChannel;
import org.json.JSONObject;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;


public class PublishMessageBranch {
    private MessageChannel messageChannel;

    public PublishMessageBranch(MessageChannel messageChannel) {
        this.messageChannel = messageChannel;
    }

    public Observable<RemoteConnectionJob.Result> publish() {
        MessageBranchInfo info = messageChannel.getBranch().getMessageBranchInfo();
        List<MessageBranchInfo.Participant> participantList = info.getParticipants();
        UserIdentity userIdentity = messageChannel.getBranch().getIdentity();

        List<Observable<RemoteConnectionJob.Result>> observableList = new ArrayList<>();
        for (MessageBranchInfo.Participant participant : participantList) {
            ConnectionInfo connectionInfo = null;
            RemoteConnectionJob remoteConnectionJob = null;
            Contact contactPublic = userIdentity.getContactFinder().find(participant.uid);
            if (participant.uid.equals("") || contactPublic == null) {
                //contact request
                String[] parts = participant.address.split("@");
                if (parts.length != 2)
                    continue;
                String server = parts[1];
                String serverUser = parts[0];

                connectionInfo = new ConnectionInfo(server, serverUser, userIdentity.getMyself());
                ContactRequest contactRequest = new ContactRequest(connectionInfo, messageChannel.getBranch().getIdentity());

                remoteConnectionJob = contactRequest.getContactRequestJob();
            } else if (connectionInfo != null) {
                connectionInfo = new ConnectionInfo(contactPublic.getServer(), contactPublic.getServerUser(),
                        userIdentity.getMyself());
            }

            InitPublishMessageBranchJob initPublishMessageBranchJob = new InitPublishMessageBranchJob(messageChannel,
                    connectionInfo, contactPublic);
            if (remoteConnectionJob != null)
                remoteConnectionJob.setFollowUpJob(initPublishMessageBranchJob);
            else
                remoteConnectionJob = initPublishMessageBranchJob;

            final RemoteConnection remoteConnection = ConnectionManager.get().getConnection(connectionInfo);
            observableList.add(remoteConnection.queueJob(remoteConnectionJob));
        }

        return Observable.merge(Observable.from(observableList));
    }

    class InitPublishMessageBranchJob extends JsonRemoteConnectionJob {
        final private MessageChannel messageChannel;
        final private ConnectionInfo connectionInfo;
        final private Contact receiver;

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

            int transactionId = result.getInt("transactionId");
            byte[] signedToken = messageChannel.sign(result.getString("signToken"));
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

            String localTip = messageChannel.getBranch().getMessageStorage().getDatabase().getTip();
            String remoteTip = result.getString("remoteTip");
            if (localTip.equals(remoteTip))
                return new Result(Result.DONE, "branch in sync");

            setFollowUpJob(new Sync(messageChannel.getBranch().getMessageStorage().getDatabase(),
                    connectionInfo.serverUser, getRemoteId()));

            return new Result(Result.DONE, getMessage(result));
        }

        private String getRemoteId() {
            return connectionInfo.serverUser + "@" + connectionInfo.server + ":" + messageChannel.getBranchName();
        }
    }
}
