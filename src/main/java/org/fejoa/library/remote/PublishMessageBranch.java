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
                    connectionInfo);
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

        public InitPublishMessageBranchJob(MessageChannel messageChannel, ConnectionInfo connectionInfo) {
            this.messageChannel = messageChannel;
            this.connectionInfo = connectionInfo;
        }

        @Override
        public byte[] getRequest() throws Exception {
            return jsonRPC.call("init_publish_branch",
                    new JsonRPC.Argument("branch", messageChannel.getBranchName())).getBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            JSONObject result = jsonRPC.getReturnValue(new String(reply));
            if (getStatus(result) != 0)
                return new Result(Result.ERROR, getMessage(result));

            byte[] signedToken = null;
            if (result.has("authRequestToken")) {
                String authRequestToken = result.getString("authRequestToken");
                signedToken = messageChannel.sign(authRequestToken);
            }

            setFollowUpJob(new LoginPublishMessageBranchJob(messageChannel, connectionInfo, signedToken));

            return new Result(Result.DONE, getMessage(result));
        }
    }

    class LoginPublishMessageBranchJob extends JsonRemoteConnectionJob {
        final private MessageChannel messageChannel;
        final private ConnectionInfo connectionInfo;
        final private byte[] signedToken;

        public LoginPublishMessageBranchJob(MessageChannel messageChannel, ConnectionInfo connectionInfo,
                                            byte[] signedToken) {
            this.messageChannel = messageChannel;

            this.connectionInfo = connectionInfo;
            this.signedToken = signedToken;
        }

        @Override
        public byte[] getRequest() throws Exception {
            return jsonRPC.call("login_publish_branch",
                    new JsonRPC.Argument("signed_token", Base64.encodeBytes(signedToken)),
                    new JsonRPC.Argument("branch", messageChannel.getBranchName()),
                    new JsonRPC.Argument("tip", messageChannel.getBranch().getMessageStorage().getDatabase().getTip()))
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

            setFollowUpJob(new Sync(messageChannel.getBranch().getMessageStorage().getDatabase(), getRemoteId()));

            return new Result(Result.DONE, getMessage(result));
        }

        private String getRemoteId() {
            return connectionInfo.serverUser + "@" + connectionInfo.server + ":" + messageChannel.getBranchName();
        }
    }
}
