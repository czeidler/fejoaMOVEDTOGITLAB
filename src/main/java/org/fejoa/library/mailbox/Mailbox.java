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
import org.fejoa.library.support.ObservableGetter;
import org.fejoa.library.support.WeakListenable;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class Mailbox extends UserData {
    public interface Listener {
        public void onMessageChannelAdded(MessageChannelRef channelRef);
    }

    final private WeakListenable<Listener> mailboxListeners = new WeakListenable();
    final private ICryptoInterface crypto = Crypto.get();
    final private List<MessageChannelRef> messageChannels = new ArrayList<>();
    final private UserIdentity userIdentity;

    private MailboxBookkeeping bookkeeping;
    private MailboxSyncManager mailboxSyncManager;

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
                        channel = loadMessageChannel(channelUid);
                    } catch (Exception e) {
                        observer.onError(e);
                    }

                    observer.onNext(channel);
                    observer.onCompleted();
                    return Subscriptions.empty();
                }
            });
        }

        @Override
        protected MessageChannel getCached() {
            return messageChannelCache.get(channelUid);
        }
    }

    // create new
    public Mailbox(UserIdentity userIdentity) throws IOException, CryptoException {
        this.userIdentity = userIdentity;

        byte hashResult[] = CryptoHelper.sha1Hash(crypto.generateInitializationVector(40));
        uid = CryptoHelper.toHex(hashResult);
    }

    // load existing
    public Mailbox(SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder, IUserIdentityFinder userIdentityFinder)
            throws IOException, CryptoException {
        readUserData(storageDir, keyStoreFinder);

        String userIdentityId = storageDir.readSecureString("user_identity");
        userIdentity = userIdentityFinder.find(userIdentityId);

        loadMessageChannels();

        initBookkeeping();
    }

    private void initBookkeeping() throws IOException, CryptoException {
        bookkeeping = new MailboxBookkeeping(".gitMailboxBookkeeping", this);
    }

    public void startSyncing(INotifications notifications) {
        mailboxSyncManager = new MailboxSyncManager(this, notifications);
    }

    public UserIdentity getUserIdentity() {
        return userIdentity;
    }

    public MessageChannel createNewMessageChannel() throws CryptoException, IOException {
        return new MessageChannel(getStorageDir().getDatabase().getPath(), this);
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

    @Override
    public void commit() throws IOException {
        super.commit();
        bookkeeping.commit(getStorageDir().getTip());
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

        storageDir.writeSecureString("user_identity", userIdentity.getUid());

        if (bookkeeping == null)
            initBookkeeping();
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
        MessageChannel messageChannel = new MessageChannel(channelStorage, this);
        messageChannelCache.put(branchId, messageChannel);
        return messageChannel;
    }

    private SecureStorageDir getChannelStorageDir(String branchName) {
        return new SecureStorageDir(storageDir, branchName.substring(0, 2) + "/" + branchName.substring(2));
    }

    public void addMessageChannel(MessageChannel messageChannel) throws CryptoException, IOException {
        String branchName = messageChannel.getBranchName();
        SecureStorageDir dir = getChannelStorageDir(branchName);

        ContactPrivate myself = userIdentity.getMyself();
        messageChannel.write(dir, myself, myself.getMainKeyId());
        dir.writeSecureString("branchTip", messageChannel.getBranch().getTip());
        messageChannelCache.put(branchName, messageChannel);

        addChannelToList(new MessageChannelRef(branchName));
    }

    public void onBranchCommited(MessageChannel channel) throws IOException, CryptoException {
        String branchName = channel.getBranchName();
        SecureStorageDir dir = getChannelStorageDir(branchName);
        dir.writeSecureString("branchTip", channel.getBranch().getTip());

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
