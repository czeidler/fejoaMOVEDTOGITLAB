/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.INotifications;
import org.fejoa.library.Profile;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.FejoaEnvironment;
import org.fejoa.library.mailbox.Mailbox;
import org.fejoa.library.mailbox.MailboxSyncManager;
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library.mailbox.Messenger;
import org.fejoa.library.remote.*;
import org.fejoa.library.support.IFejoaSchedulers;
import org.fejoa.library.support.StorageLib;
import rx.Observer;
import rx.Scheduler;
import rx.concurrency.Schedulers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


class FejoaSchedulersCMD implements IFejoaSchedulers {
    @Override
    public Scheduler mainScheduler() {
        return Schedulers.immediate();
    }
}

class Client {
    final ConnectionManager connectionManager = new ConnectionManager(new FejoaSchedulersCMD());
    MailboxSyncManager mailboxSyncManager;
    Profile profile = null;
    final INotifications notifications;

    public Client(INotifications notifications) {
        this.notifications = notifications;
    }

    public void openOrCreate(String userName, String server, String password) throws Exception {
        File userNameDir = new File(userName);
        userNameDir.mkdirs();

        FejoaEnvironment environment = new FejoaEnvironment(userNameDir.getPath());
        try {
            profile = new Profile(environment, "profile", "");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        boolean opened = false;
        try {
            opened = profile.open(password);
        } catch (Exception e) {
            System.out.println("failed to open account");
        }
        if (!opened) {
            System.out.println("Creating new profile (" + userName + ")...");
            profile.createNew(password, CryptoSettings.getFast());

            ContactPrivate myself = profile.getMainUserIdentity().getMyself();
            myself.setServerUser(userName);
            myself.setServer(server);
            myself.write();

            profile.setEmptyRemotes(server, userName, myself);
            profile.commit();
        }

        // copy signature to server
        File signatureFile = new File(userName, Profile.SIGNATURE_FILE);
        File targetDir = getServerDir(userName);
        targetDir.mkdirs();
        if (!StorageLib.copyFile(signatureFile, new File(targetDir, Profile.SIGNATURE_FILE)))
            throw new Exception("Can't copy signature to server.");
    }

    private File getServerDir(String userName) {
        return new File("../php_server/" + userName);
    }

    public void deleteLocal(String userName) {
        StorageLib.recursiveDeleteFile(new File(userName));
    }

    public void deleteServer(String userName) {
        StorageLib.recursiveDeleteFile(getServerDir(userName));
    }

    public void start() {
        List<RemoteStorageLink> links = new ArrayList<>(profile.getRemoteStorageLinks().values());

        connectionManager.startWatching(links, new ServerWatcher.IListener() {
            @Override
            public void onBranchesUpdated(List<RemoteStorageLink> links) {
                for (RemoteStorageLink link : links)
                    syncBranch(link);
            }

            @Override
            public void onError(String message) {
                notifications.error("WatchListener: " + message);
            }

            @Override
            public void onResult(RemoteConnectionJob.Result args) {
                notifications.info("WatchListener: " + args.message);
            }
        });

        mailboxSyncManager = new MailboxSyncManager(connectionManager, profile.getMainMailbox(), notifications);
    }

    public void stop() {
        connectionManager.shutdown(false);
        mailboxSyncManager = null;
    }

    private void syncBranch(RemoteStorageLink remoteStorageLink) {
        final String branch = remoteStorageLink.getLocalStorage().getBranch();
        ServerSync serverSync = new ServerSync(connectionManager, remoteStorageLink);
        serverSync.sync().subscribe(new Observer<RemoteConnectionJob.Result>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                notifications.error("sync error");
            }

            @Override
            public void onNext(RemoteConnectionJob.Result result) {
                if (result.status == RemoteConnectionJob.Result.DONE)
                    notifications.info(branch + ": sync ok");
                else
                    notifications.error(branch + ": sync failed");
            }
        });
    }

    public void sendNewMessage(String receiver, String subject, String body) throws CryptoException, IOException {
        List<String> receivers = new ArrayList<>();
        receivers.add(receiver);

        Mailbox mailbox = profile.getMainMailbox();
        Messenger messenger = new Messenger(mailbox);
        messenger.createNewThreadMessage(subject, receivers, body);
    }

    public void reply(MessageChannel messageChannel, String body) throws IOException, CryptoException {
        Mailbox mailbox = profile.getMainMailbox();
        Messenger messenger = new Messenger(mailbox);
        messenger.addMessageToThread(messageChannel, body);
    }

    public Mailbox getMailbox() {
        return profile.getMainMailbox();
    }
}

public class ClientInteraction extends TestCase {
    class ErrorNotifications implements INotifications {
        boolean hasError = false;
        String error = "";

        @Override
        public void info(String message) {

        }

        @Override
        public void error(String error) {
            hasError = true;
            this.error = error;
        }
    }

    ErrorNotifications client1Error = new ErrorNotifications();
    ErrorNotifications client2Error = new ErrorNotifications();

    final int WAIT_TIME = 2000;

    private void waitAndCheck(String tag, int time, ErrorNotifications notifications) throws Exception {
        Thread.sleep(time);

        if (notifications.hasError)
            throw new Exception(tag + ": " + notifications.error);
    }

    private void client1CheckResults() throws Exception {
        waitAndCheck("Client1", WAIT_TIME, client1Error);
    }

    private void client2CheckResults() throws Exception {
        waitAndCheck("Client2", WAIT_TIME, client1Error);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testTwoClients() throws Exception {
        String client1UserName = "client1";
        Client client1 = new Client(client1Error);
        client1.deleteLocal(client1UserName);
        client1.deleteServer(client1UserName);
        client1.openOrCreate(client1UserName, "localhost", "client1Password");

        String client2UserName = "client2";
        Client client2 = new Client(client2Error);
        client2.deleteLocal(client2UserName);
        client2.deleteServer(client2UserName);
        client2.openOrCreate(client2UserName, "localhost", "client2Password");

        client1.start();
        client1CheckResults();

        client2.start();
        client2CheckResults();

        client1.sendNewMessage("client2@localhost", "test", "test body");
        client1CheckResults();

        client2CheckResults();
        assertEquals(1, client2.getMailbox().getNumberOfMessageChannels());

        client1.stop();
        client2.stop();
        client1CheckResults();
        client2CheckResults();
    }

}
