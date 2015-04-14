/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.INotifications;
import org.fejoa.library.remote.ConnectionManager;
import org.fejoa.library.remote.PublishMessageBranch;
import org.fejoa.library.remote.RemoteConnectionJob;
import org.fejoa.library.remote.SyncResultData;
import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.util.functions.Func1;

import java.io.IOException;
import java.util.List;


public class MailboxSyncManager {
    final ConnectionManager connectionManager;
    final private Mailbox mailbox;
    final INotifications notifications;
    private SyncCookie syncCookie = null;

    // keep a reference of the listener!
    private MailboxBookkeeping.IListener listener = new MailboxBookkeeping.IListener() {
        @Override
        public void onDirtyBranches() {
            sync();
        }
    };

    public MailboxSyncManager(ConnectionManager connectionManager, Mailbox mailbox, INotifications notifications) {
        this.connectionManager = connectionManager;
        this.mailbox = mailbox;
        this.notifications = notifications;
        mailbox.getBookkeeping().addListener(listener);
        sync();
    }

    class SyncCookie {
        final public int nJobs;
        public int jobDone = 0;
        public boolean bookkeepingNeedsCommit = false;

        public SyncCookie(int nJobs) {
            this.nJobs = nJobs;
        }
    }

    private void sync() {
        if (syncCookie != null)
            return;

        MailboxBookkeeping bookkeeping = mailbox.getBookkeeping();
        List<String> dirtyBranches = bookkeeping.getAllDirtyBranches();
        if (dirtyBranches.size() == 0)
            return;
        syncCookie = new SyncCookie(dirtyBranches.size());
        Scheduler observerScheduler = connectionManager.getFejoaSchedulers().mainScheduler();
        for (String branch : dirtyBranches) {
            Mailbox.MessageChannelRef ref = mailbox.getMessageChannel(branch);
            ref.get(observerScheduler).mapMany(new Func1<MessageChannel, Observable<RemoteConnectionJob.Result>>() {
                @Override
                public Observable<RemoteConnectionJob.Result> call(MessageChannel messageChannel) {
                    PublishMessageBranch publishMessageBranch = new PublishMessageBranch(connectionManager,
                            messageChannel);
                    return publishMessageBranch.publish();
                }
            }).subscribe(new Observer<RemoteConnectionJob.Result>() {
                @Override
                public void onCompleted() {
                    syncCookie.jobDone++;
                    checkSyncDone();
                }

                @Override
                public void onError(Throwable e) {
                    notifications.error(e.getMessage());

                    syncCookie.jobDone++;
                    checkSyncDone();
                }

                @Override
                public void onNext(RemoteConnectionJob.Result result) {
                    if (result.status < RemoteConnectionJob.Result.DONE)
                        notifications.error(result.message);
                    else {
                        notifications.info(result.message);

                        if (result.hasData() && result.data instanceof SyncResultData) {
                            SyncResultData syncResultData = (SyncResultData) result.data;
                            MailboxBookkeeping bookkeeping = mailbox.getBookkeeping();
                            try {
                                if (bookkeeping.cleanDirtyBranch(syncResultData.remoteUid, syncResultData.branch))
                                    syncCookie.bookkeepingNeedsCommit = true;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }
    }

    private void checkSyncDone() {
        if (syncCookie.jobDone < syncCookie.nJobs)
            return;

        if (syncCookie.bookkeepingNeedsCommit) {
            MailboxBookkeeping bookkeeping = mailbox.getBookkeeping();
            if (bookkeeping.getAllDirtyBranches().size() == 0) {
                try {
                    bookkeeping.commit();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        syncCookie = null;
        sync();
    }
}
