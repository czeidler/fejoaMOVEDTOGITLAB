package org.fejoa.library.mailbox;


import org.fejoa.library.INotifications;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.ConnectionManager;
import org.fejoa.library.remote.PublishMessageBranch;
import org.fejoa.library.remote.RemoteConnectionJob;
import rx.Observable;
import rx.Observer;

import java.io.IOException;
import java.util.List;

public class Messenger {
    final private Mailbox mailbox;

    public Messenger(Mailbox mailbox) {
        this.mailbox = mailbox;
    }

    public MessageChannel createNewThreadMessage(String subject, List<String> receivers, String body) throws CryptoException, IOException {

        MessageBranchInfo branchInfo = new MessageBranchInfo();
        branchInfo.setSubject(subject);
        for (String receiver : receivers)
            branchInfo.addParticipant(receiver, "");

        Message message = new Message();
        message.setBody(body);

        MessageChannel messageChannel = mailbox.createNewMessageChannel();
        MessageBranch messageBranch = messageChannel.getBranch();
        messageBranch.setMessageBranchInfo(branchInfo);
        messageBranch.addMessage(message);
        messageBranch.commit();

        mailbox.addMessageChannel(messageChannel);
        mailbox.commit();

        return messageChannel;
    }

    public void addMessageToThread(MessageChannel channel, String body) throws IOException, CryptoException {
        Message message = new Message();
        message.setBody(body);

        MessageBranch messageBranch = channel.getBranch();
        messageBranch.addMessage(message);
        messageBranch.commit();

        mailbox.commit();
    }

    public Observable<RemoteConnectionJob.Result> publishMessageChannel(MessageChannel channel) {
        PublishMessageBranch publishMessageBranch = new PublishMessageBranch(channel);
        return publishMessageBranch.publish();
    }

    public void sendMessageChannel(MessageChannel channel, final INotifications notifications) {
        publishMessageChannel(channel).subscribe(new Observer<RemoteConnectionJob.Result>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                notifications.error(e.getMessage());
            }

            @Override
            public void onNext(RemoteConnectionJob.Result result) {
                if (result.status < RemoteConnectionJob.Result.DONE)
                    notifications.error(result.message);
                else
                    notifications.info(result.message);
            }
        });
    }
}
