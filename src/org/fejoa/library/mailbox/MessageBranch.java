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
import org.fejoa.library.crypto.CryptoHelper;

import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;


class MessageChannel extends Channel {
    private MessageBranch branch;

    // create new
    public MessageChannel(String messageBranchPath, UserIdentity identity) throws CryptoException, IOException {
        create();

        SecureStorageDir branchStorage = SecureStorageDirBucket.get(messageBranchPath, getBranchName());
        branch = new MessageBranch(branchStorage, identity, getParcelCrypto());
    }

    // load
    public MessageChannel(SecureStorageDir dir, UserIdentity identity) throws IOException, CryptoException {
        load(dir, identity);
    }

    public MessageBranch getBranch() {
        return branch;
    }

    public void write(SecureStorageDir dir, ContactPrivate sender, KeyId senderKey) throws CryptoException, IOException {
        dir.writeString("signature_key", CryptoHelper.convertToPEM(signatureKeyPublic));
        byte[] pack = pack(sender, senderKey, sender, senderKey);
        dir.writeBytes("d", pack);
        dir.writeSecureString("database_path", dir.getDatabase().getPath());
    }

    private void load(SecureStorageDir dir, UserIdentity identity)
            throws IOException, CryptoException {
        PublicKey publicKey = CryptoHelper.publicKeyFromPem(dir.readString("signature_key"));
        byte[] data = dir.readBytes("d");
        load(identity, publicKey, data);

        String databasePath = dir.readSecureString("database_path");
        SecureStorageDir messageStorage = SecureStorageDirBucket.get(databasePath, getBranchName());

        branch = new MessageBranch(messageStorage, identity, getParcelCrypto());
    }
}


public class MessageBranch {
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
    }

}
