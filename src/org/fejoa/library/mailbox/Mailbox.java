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
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class Mailbox extends UserData {

    public interface Listener {
        public void onMessageChannelAdded(MessageChannelRef branch);
    }

    final private WeakListenable<Listener> mailboxListeners = new WeakListenable();
    final private ICryptoInterface crypto = Crypto.get();
    final private List<MessageChannelRef> messageChannels = new ArrayList<>();
    final private UserIdentity userIdentity;

    final private int CACHE_SIZE = 10;
    final private Map messageChannelCache = new LinkedHashMap<String, MessageChannel>(CACHE_SIZE + 1, .75F, true) {
            // This method is called just after a new entry has been added
            public boolean removeEldestEntry (Map.Entry eldest){
                return size() > CACHE_SIZE;
            }
        };

    class MessageChannelRef {
        final private String channelUid;

        public MessageChannelRef(String channel) {
            this.channelUid = channel;
        }

        public MessageChannel get() throws IOException, CryptoException {
            MessageChannel channel = (MessageChannel)messageChannelCache.get(channelUid);
            if (channel != null)
                return channel;
            return loadMessageChannel(channelUid);
        }
    }

    public Mailbox(UserIdentity userIdentity) {
        this.userIdentity = userIdentity;

        byte hashResult[] = CryptoHelper.sha1Hash(crypto.generateInitializationVector(40));
        uid = CryptoHelper.toHex(hashResult);
    }

    public Mailbox(SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder, IUserIdentityFinder userIdentityFinder)
            throws IOException, CryptoException {
        readUserData(storageDir, keyStoreFinder);

        String userIdentityId = storageDir.readSecureString("user_identity");
        userIdentity = userIdentityFinder.find(userIdentityId);

        loadMessageChannels();
    }

    public void write(SecureStorageDir storageDir) throws IOException, CryptoException {
        writeUserData(uid, storageDir);

        storageDir.writeSecureString("user_identity", userIdentity.getUid());
    }

    private void loadMessageChannels() throws IOException, CryptoException {
        List<String> part1List = storageDir.listDirectories("");
        for (String part1 : part1List) {
            for (String part2 : storageDir.listDirectories(part1))
                addChannelToList(new MessageChannelRef(part1 + part2));
        }
    }

    private MessageChannel loadMessageChannel(String channelId) throws IOException, CryptoException {
        SecureStorageDir channelStorage = new SecureStorageDir(storageDir,
                channelId.substring(0, 1) + "/" + channelId.substring(2));
        MessageChannel messageChannel = new MessageChannel(channelStorage, userIdentity);

        return messageChannel;
    }

    public void addMessageChannel(MessageChannel messageChannel) throws CryptoException, IOException {
        String branchName = messageChannel.getBranchName();
        SecureStorageDir dir = new SecureStorageDir(storageDir,
                branchName.substring(0, 1) + "/" + branchName.substring(2));

        ContactPrivate myself = userIdentity.getMyself();
        messageChannel.write(dir, myself, myself.getMainKeyId());
        messageChannelCache.put(branchName, messageChannel);

        addChannelToList(new MessageChannelRef(branchName));
    }

    private void addChannelToList(MessageChannelRef messageChannelRef) {
        messageChannels.add(messageChannelRef);
        notifyOnBranchAdded(messageChannelRef);
    }

    private void notifyOnBranchAdded(MessageChannelRef messageChannelRef) {
        for (Listener listener : mailboxListeners.getListeners())
            listener.onMessageChannelAdded(messageChannelRef);
    }
}
