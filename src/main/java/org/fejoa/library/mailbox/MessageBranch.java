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
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.DatabaseDir;
import org.fejoa.library.database.SecureStorageDir;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MessageBranch extends WeakListenable<MessageBranch.IListener> {
    public interface IListener {
        void onMessageAdded(Message message);
        void onCommit();
    }

    private ParcelCrypto parcelCrypto;
    private MessageBranchInfo messageBranchInfo;
    private List<Message> messages = new ArrayList<>();
    private SecureStorageDir messageStorage;

    private Mailbox mailbox;
    private UserIdentity identity;

    static public MessageBranch createNewMessageBranch(SecureStorageDir messageStorage, Mailbox mailbox,
                                                   ParcelCrypto parcelCrypto) throws IOException, CryptoException {
        return new MessageBranch(messageStorage, mailbox, parcelCrypto);
    }

    static public MessageBranch loadMessageBranch(SecureStorageDir messageStorage, Mailbox mailbox,
                                                  ParcelCrypto parcelCrypto) throws IOException, CryptoException {
        MessageBranch messageBranch = new MessageBranch(messageStorage, mailbox, parcelCrypto);
        messageBranch.load();
        return messageBranch;
    }

    private StorageDir.IListener storageListener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff, String base, String tip) {
            DatabaseDir added = diff.added;
            List<DatabaseDir> firstParts = added.getDirectories();
            for (DatabaseDir firstPart : firstParts) {
                List<String> secondParts = firstPart.getFiles();
                for (String secondPart : secondParts) {
                    String firstPartName = firstPart.getDirName();
                    if (isMessageInList(firstPartName + secondPart))
                        continue;
                    try {
                        byte[] pack = messageStorage.readBytes(firstPartName + "/" + secondPart);
                        Message message = new Message();
                        message.load(parcelCrypto, identity, pack);

                        addMessageToList(message);
                    } catch (Exception e) {

                    }
                }
            }
        }
    };

    private boolean isMessageInList(String messageUid) {
        for (Message message : messages) {
            if (message.getUid().equals(messageUid))
                return true;
        }
        return false;
    }

    private MessageBranch(SecureStorageDir messageStorage, Mailbox mailbox, ParcelCrypto parcelCrypto)
            throws IOException,
            CryptoException {
        this.messageStorage = messageStorage;
        this.mailbox = mailbox;
        this.identity = mailbox.getUserIdentity();
        this.parcelCrypto = parcelCrypto;

        this.messageStorage.addListener(storageListener);
    }

    public String getTip() throws IOException {
        return messageStorage.getTip();
    }

    public void commit() throws IOException {
        messageStorage.commit();
        notifyCommit();
    }

    private void load() throws IOException, CryptoException {
        loadMessageBranchInfo();
        loadMessages();
    }

    public void setMessageBranchInfo(MessageBranchInfo info, CryptoSettings signatureSettings) throws CryptoException,
            IOException {
        ContactPrivate myself = identity.getMyself();
        byte[] pack = info.write(parcelCrypto, myself, myself.getMainKeyId(), signatureSettings);
        messageStorage.writeBytes("i", pack);

        messageBranchInfo = info;
    }

    public MessageBranchInfo getMessageBranchInfo() {
        return messageBranchInfo;
    }

    public SecureStorageDir getMessageStorage() {
        return messageStorage;
    }

    private void loadMessageBranchInfo() throws IOException, CryptoException {
        byte[] pack = messageStorage.readBytes("i");

        MessageBranchInfo info = new MessageBranchInfo();
        info.load(parcelCrypto, identity.getContactFinder(), pack);

        messageBranchInfo = info;
    }

    public int getNumberOfMessages() {
        return messages.size();
    }

    public Message getMessage(int index) {
        return messages.get(index);
    }

    public void addMessage(Message message, CryptoSettings signatureSettings) throws IOException, CryptoException {
        ContactPrivate myself = identity.getMyself();
        byte[] pack = message.write(parcelCrypto, myself, myself.getMainKeyId(), signatureSettings);

        // write message
        String uid = message.getUid();
        SecureStorageDir dir = new SecureStorageDir(messageStorage, uid.substring(0, 2));
        dir.writeBytes(uid.substring(2), pack);

        addMessageToList(message);
    }

    private void loadMessages() throws IOException {
        List<String> firstParts = messageStorage.listDirectories("");
        for (String firstPart : firstParts) {
            List<String> secondParts = messageStorage.listFiles(firstPart);
            for (String secondPart : secondParts) {
                try {
                    byte[] pack = messageStorage.readBytes(firstPart + "/" + secondPart);
                    Message message = new Message();
                    message.load(parcelCrypto, identity, pack);

                    addMessageToList(message);
                } catch (Exception e) {

                }

            }
        }
    }

    public UserIdentity getIdentity() {
        return identity;
    }

    private void addMessageToList(Message message) {
        messages.add(message);
        notifyMessageAdded(message);
    }

    private void notifyMessageAdded(Message message) {
        for (IListener listener : getListeners())
            listener.onMessageAdded(message);
    }

    private void notifyCommit() {
        for (IListener listener : getListeners())
            listener.onCommit();
    }
}
