/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.*;
import org.fejoa.library.crypto.*;
import org.fejoa.library.database.*;
import org.fejoa.library.support.ObservableGetter;
import org.fejoa.library.support.WeakListenable;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class Mailbox extends UserData {
    public interface Listener {
        void onMessageChannelAdded(MessageChannelRef channelRef);
    }

    final FejoaEnvironment environment;
    final private WeakListenable<Listener> mailboxListeners = new WeakListenable();
    final private ICryptoInterface crypto = Crypto.get();
    final private List<MessageChannelRef> messageChannels = new ArrayList<>();
    final private UserIdentity userIdentity;

    private MailboxBookkeeping bookkeeping;

    final private int CACHE_SIZE = 10;
    final private Map<String, MessageChannel> messageChannelCache = new LinkedHashMap(CACHE_SIZE + 1, .75F, true) {
            // This method is called just after a new entry has been added
            public boolean removeEldestEntry (Map.Entry eldest){
                return size() > CACHE_SIZE;
            }
        };

    public void clearMessageChannelCache() {
        messageChannelCache.clear();
    }

    public class MessageChannelRef extends ObservableGetter<MessageChannel> {
        final private String channelUid;

        public MessageChannelRef(String channel) {
            this.channelUid = channel;
        }

        public String getChannelUid() {
            return channelUid;
        }

        @Override
        protected Observable<MessageChannel> createWorker() {
            return Observable.create(new Observable.OnSubscribeFunc<MessageChannel>() {
                @Override
                public Subscription onSubscribe(Observer<? super MessageChannel> observer) {
                    MessageChannel channel = null;
                    try {
                        channel = getNow();
                    } catch (Exception e) {
                        observer.onError(e);
                    }

                    observer.onNext(channel);
                    observer.onCompleted();
                    return Subscriptions.empty();
                }
            });
        }

        public MessageChannel getNow() throws IOException, CryptoException {
            return loadMessageChannel(channelUid);
        }

        @Override
        public MessageChannel getCached() {
            return messageChannelCache.get(channelUid);
        }
    }

    private StorageDir.IListener storageListener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff, String base, String tip) {
            updateBookkeeping(diff, base, tip);
        }
    };

    private void updateBookkeeping(DatabaseDiff diff, String base, String tip) {
        String bookkeepingMailboxTip = bookkeeping.getMailboxTip();
        if (!bookkeepingMailboxTip.equals(base))
            throw new RuntimeException("TODO: handle this case!");

        try {
            DatabaseDir added = diff.added;
            // TODO maybe only watch the base dir?
            DatabaseDir baseDir = added.findDirectory(storageDir.getBaseDir());
            if (baseDir == null)
                return;

            List<DatabaseDir> part1List = baseDir.getDirectories();
            for (DatabaseDir part1 : part1List) {
                for (DatabaseDir part2 : baseDir.findDirectory(part1.getDirName()).getDirectories()) {
                    String channelId = part1.getDirName() + part2.getDirName();
                    if (!hasChannel(channelId))
                        addChannelToList(new MessageChannelRef(channelId));

                    try {
                        bookkeeping.markAsDirty(getUserIdentity().getMyself().getAddress(), channelId);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            try {
                bookkeeping.commit(tip);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // create new
    public Mailbox(FejoaEnvironment environment, UserIdentity userIdentity) throws IOException, CryptoException {
        this.environment = environment;
        this.userIdentity = userIdentity;

        byte hashResult[] = CryptoHelper.sha1Hash(crypto.generateInitializationVector(40));
        uid = CryptoHelper.toHex(hashResult);
    }

    // load existing
    public Mailbox(FejoaEnvironment environment, SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder,
                   IUserIdentityFinder userIdentityFinder) throws IOException, CryptoException {
        this.environment = environment;

        readUserData(storageDir, keyStoreFinder);

        String userIdentityId = storageDir.readSecureString("userIdentity");
        userIdentity = userIdentityFinder.find(userIdentityId);

        loadMessageChannels();

        initBookkeeping();

        this.storageDir.addListener(storageListener);
    }

    public FejoaEnvironment getEnvironment() {
        return environment;
    }

    private void initBookkeeping() throws IOException, CryptoException {
        File file = new File(new File(storageDir.getDatabasePath()).getParentFile(), ".gitMailboxBookkeeping");
        bookkeeping = new MailboxBookkeeping(file.getPath(), this);

        String bookkeepingMailboxTip = bookkeeping.getMailboxTip();
        String mailBoxTip = storageDir.getTip();
        if (!mailBoxTip.equals(bookkeepingMailboxTip) && !mailBoxTip.equals("")) {
            DatabaseDiff diff = storageDir.getDiff(bookkeepingMailboxTip, mailBoxTip);
            updateBookkeeping(diff, bookkeepingMailboxTip, mailBoxTip);
        }
    }

    public UserIdentity getUserIdentity() {
        return userIdentity;
    }

    public String getMessageStoragePath() {
        return getStorageDir().getDatabasePath();
    }

    public MessageChannel createNewMessageChannel() throws CryptoException, IOException {
        return new MessageChannel(environment, getMessageStoragePath(), this);
    }

    public MailboxBookkeeping getBookkeeping() {
        return bookkeeping;
    }

    public int getNumberOfMessageChannels() {
        return messageChannels.size();
    }

    public MessageChannelRef getMessageChannel(int index) {
        return messageChannels.get(index);
    }

    public MessageChannelRef getMessageChannel(String branchName) {
        for (MessageChannelRef ref : messageChannels) {
            if (ref.getChannelUid().equals(branchName))
                return ref;
        }
        return null;
    }

    public void write(SecureStorageDir storageDir) throws IOException, CryptoException {
        writeUserData(uid, storageDir);

        this.storageDir.writeSecureString("userIdentity", userIdentity.getUid());

        if (bookkeeping == null)
            initBookkeeping();

        this.storageDir.addListener(storageListener);
    }

    private void loadMessageChannels() throws IOException, CryptoException {
        List<String> part1List = storageDir.listDirectories("");
        for (String part1 : part1List) {
            for (String part2 : storageDir.listDirectories(part1))
                addChannelToList(new MessageChannelRef(part1 + part2));
        }
    }

    private MessageChannel loadMessageChannel(final String branchId) throws IOException, CryptoException {
        SecureStorageDir channelStorage = new SecureStorageDir(storageDir,
                branchId.substring(0, 2) + "/" + branchId.substring(2));
        MessageChannel messageChannel = new MessageChannel(environment, channelStorage, this);
        // only cache if the branch is there and loaded
        if (messageChannel.getBranch() != null)
            messageChannelCache.put(branchId, messageChannel);
        return messageChannel;
    }

    private SecureStorageDir getChannelStorageDir(String branchName) {
        return new SecureStorageDir(storageDir, branchName.substring(0, 2) + "/" + branchName.substring(2));
    }

    private boolean hasChannel(String channelId) {
        for (MessageChannelRef ref : messageChannels) {
            if (ref.getChannelUid().equals(channelId))
                return true;
        }
        return false;
    }

    public void addMessageChannel(MessageChannel messageChannel) throws CryptoException, IOException {
        final String branchName = messageChannel.getBranchName();
        final SecureStorageDir dir = getChannelStorageDir(branchName);

        final ContactPrivate myself = userIdentity.getMyself();
        messageChannel.write(dir, myself, myself.getMainKeyId());
        dir.writeString("branchTip", messageChannel.getBranch().getTip());
        messageChannelCache.put(branchName, messageChannel);
    }

    public void onBranchCommitted(MessageChannel channel) throws IOException, CryptoException {
        final String branchName = channel.getBranchName();
        final SecureStorageDir dir = getChannelStorageDir(branchName);
        dir.writeString("branchTip", channel.getBranch().getTip());

        for (MessageBranchInfo.Participant participant : channel.getBranch().getMessageBranchInfo().getParticipants())
            bookkeeping.markAsDirty(participant.address, branchName);
    }

    private void addChannelToList(MessageChannelRef messageChannelRef) {
        messageChannels.add(messageChannelRef);
        notifyOnBranchAdded(messageChannelRef);
    }

    public void addListener(Listener listener) {
        mailboxListeners.addListener(listener);
    }

    public void removeListener(Listener listener) {
        mailboxListeners.removeListener(listener);
    }

    private void notifyOnBranchAdded(MessageChannelRef messageChannelRef) {
        for (Listener listener : mailboxListeners.getListeners())
            listener.onMessageChannelAdded(messageChannelRef);
    }
}
