/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MessageBranch extends WeakListenable<MessageBranch.IListener> {
    public interface IListener {
        public void onMessageAdded(Message message);
    }

    private ParcelCrypto parcelCrypto;
    private MessageBranchInfo messageBranchInfo;
    private List<Message> messages = new ArrayList<>();
    private SecureStorageDir messageStorage;

    private UserIdentity identity;

    public MessageBranch(SecureStorageDir dir, UserIdentity identity, ParcelCrypto parcelCrypto) throws IOException,
            CryptoException {
        messageStorage = dir;
        this.identity = identity;
        this.parcelCrypto = parcelCrypto;

        loadMessageBranchInfo();
        loadMessages();
    }

    public void setMessageBranchInfo(MessageBranchInfo info) throws CryptoException, IOException {
        ContactPrivate myself = identity.getMyself();
        byte[] pack = info.write(parcelCrypto, myself, myself.getMainKeyId());
        messageStorage.writeBytes("i", pack);

        messageBranchInfo = info;
    }

    private void loadMessageBranchInfo() throws IOException, CryptoException {
        byte[] pack = messageStorage.readBytes("i");

        MessageBranchInfo info = new MessageBranchInfo();
        info.load(parcelCrypto, identity, pack);

        messageBranchInfo = info;
    }

    public void addMessage(Message message) throws IOException, CryptoException {
        ContactPrivate myself = identity.getMyself();
        byte[] pack = message.write(parcelCrypto, myself, myself.getMainKeyId());

        // write message
        String uid = message.getUid();
        SecureStorageDir dir = new SecureStorageDir(messageStorage, uid.substring(0, 1));
        dir.writeBytes(uid.substring(2), pack);

        addMessageToList(message);
    }

    private void loadMessages() throws IOException {
        List<String> firstParts = messageStorage.listDirectories("");
        for (String firstPart : firstParts) {
            List<String> messageList = messageStorage.listDirectories(firstPart);
            for (String messageName : messageList) {
                try {
                    byte[] pack = messageStorage.readBytes(firstPart + "/" + messageName);
                    Message message = new Message();
                    message.load(parcelCrypto, identity, pack);

                    addMessageToList(message);
                } catch (Exception e) {

                }

            }
        }
    }

    private void addMessageToList(Message message) {
        messages.add(message);
        notifyMessageAdded(message);
    }

    private void notifyMessageAdded(Message message) {
        for (IListener listener : getListeners())
            listener.onMessageAdded(message);
    }
}
