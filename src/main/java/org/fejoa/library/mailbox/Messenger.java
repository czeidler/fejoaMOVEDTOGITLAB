package org.fejoa.library.mailbox;


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

    public void createNewThreadMessage(String subject, List<String> receivers, String body) throws CryptoException, IOException {

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
}
