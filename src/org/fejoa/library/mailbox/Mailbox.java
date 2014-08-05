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
import java.util.List;


public class Mailbox extends UserData {
    public interface Listener {
        public void onMessageChannelAdded(MessageChannel branch);
    }

    final private WeakListenable<Listener> mailboxListeners = new WeakListenable();
    final private ICryptoInterface crypto = Crypto.get();
    final private List<MessageChannel> messageChannels = new ArrayList<>();
    final private UserIdentity userIdentity;

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
        List<String> channels = storageDir.listDirectories("");
        for (String channel : channels) {
            SecureStorageDir channelStorage = new SecureStorageDir(storageDir, channel);
            MessageChannel messageChannel = new MessageChannel(channelStorage, userIdentity);

            addChannelToList(messageChannel);
        }
    }

    public void addBranch(MessageChannel messageChannel) throws CryptoException, IOException {
        SecureStorageDir dir = new SecureStorageDir(storageDir, messageChannel.getBranchName());

        ContactPrivate myself = userIdentity.getMyself();
        messageChannel.write(dir, myself, myself.getMainKeyId());

        addChannelToList(messageChannel);
    }

    private void addChannelToList(MessageChannel messageChannel) {
        messageChannels.add(messageChannel);
        notifyOnBranchAdded(messageChannel);
    }

    private void notifyOnBranchAdded(MessageChannel messageChannel) {
        for (Listener listener : mailboxListeners.getListeners())
            listener.onMessageChannelAdded(messageChannel);
    }
}
