/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.INotifications;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.PublishMessageBranch;
import org.fejoa.library.remote.RemoteConnectionJob;
import rx.Observer;

import java.io.IOException;
import java.util.List;


class MailboxSyncManager {
    final private Mailbox mailbox;
    final INotifications notifications;

    public MailboxSyncManager(Mailbox mailbox, INotifications notifications) {
        this.mailbox = mailbox;
        this.notifications = notifications;
        mailbox.getBookkeeping().addListener(new MailboxBookkeeping.IListener() {
            @Override
            public void onDirtyBranches() {
                sync();
            }
        });
    }

    private void sync() {
        MailboxBookkeeping bookkeeping = mailbox.getBookkeeping();
        List<MailboxBookkeeping.RemoteEntry> dirtyRemotes = bookkeeping.getDirtyRemotes();
        for (MailboxBookkeeping.RemoteEntry entry : dirtyRemotes) {
            for (String branch : entry.getDirtyBranches()) {
                Mailbox.MessageChannelRef ref = mailbox.getMessageChannel(branch);
                ref.get().subscribe(new Observer<MessageChannel>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(MessageChannel args) {
                        syncMessageChannel(args);
                    }
                });
            }
        }
    }

    private void syncMessageChannel(MessageChannel messageChannel) {
        PublishMessageBranch publishMessageBranch = new PublishMessageBranch(messageChannel);
        publishMessageBranch.publish().subscribe(new Observer<RemoteConnectionJob.Result>() {
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
}
