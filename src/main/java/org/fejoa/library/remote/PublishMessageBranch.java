/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.Contact;
import org.fejoa.library.UserIdentity;
import org.fejoa.library.mailbox.MessageBranchInfo;
import org.fejoa.library.mailbox.MessageChannel;
import org.json.JSONObject;
import rx.Observable;

import java.io.IOException;
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

            PublishMessageBranchJob publishMessageBranchJob = new PublishMessageBranchJob();
            if (remoteConnectionJob != null)
                remoteConnectionJob.setFollowUpJob(publishMessageBranchJob);
            else
                remoteConnectionJob = publishMessageBranchJob;

            final RemoteConnection remoteConnection = ConnectionManager.get().getConnection(connectionInfo);
            observableList.add(remoteConnection.queueJob(remoteConnectionJob));
        }

        return Observable.merge(Observable.from(observableList));
    }

    class PublishMessageBranchJob extends RemoteConnectionJob {
        private JsonRPC jsonRPC = new JsonRPC();

        @Override
        public byte[] getRequest() throws Exception {
            return jsonRPC.call("publish_branch",
                    new JsonRPC.Argument("branch", messageChannel.getBranchName())).getBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            JSONObject result = jsonRPC.getReturnValue(new String(reply));
            if (result == null)
                throw new IOException("bad return value");

            if (!result.has("status"))
                throw new IOException("no status field in return");
            String message = "";
            if (result.has("message"))
                message = result.getString("message");

            int status = result.getInt("status");
            if (status != 0)
                return new Result(Result.ERROR, message);

            return new Result(Result.DONE, message);
        }
    }
}
