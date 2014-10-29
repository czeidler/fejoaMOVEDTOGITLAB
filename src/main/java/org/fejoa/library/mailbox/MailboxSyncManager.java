/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.INotifications;
import org.fejoa.library.remote.PublishMessageBranch;
import org.fejoa.library.remote.RemoteConnectionJob;
import rx.Observer;

import java.util.List;


public class MailboxSyncManager {
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
        List<String> dirtyBranches = bookkeeping.getAllDirtyBranches();
        for (String branch : dirtyBranches) {
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
