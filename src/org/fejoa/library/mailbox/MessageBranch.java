/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.*;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.ICryptoInterface;

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
        branch = new MessageBranch(branchStorage, identity);
    }

    // load
    public MessageChannel(SecureStorageDir dir, UserIdentity identity) throws IOException, CryptoException {
        load(dir, identity);
    }

    public void write(SecureStorageDir dir, ContactPrivate sender, KeyId senderKey) throws CryptoException, IOException {
        dir.writeString("signature_key", CryptoHelper.convertToPEM(signatureKeyPublic));
        byte[] pack = pack(sender, senderKey, sender, senderKey);
        dir.writeBytes("d", pack);
        dir.writeSecureString("database_path", dir.getDatabase().getPath());
    }

    public void load(SecureStorageDir dir, UserIdentity identity)
            throws IOException, CryptoException {
        PublicKey publicKey = CryptoHelper.publicKeyFromPem(dir.readString("signature_key"));
        byte[] data = dir.readBytes("d");
        load(identity, publicKey, data);

        String databasePath = dir.readSecureString("database_path");
        SecureStorageDir messageStorage = SecureStorageDirBucket.get(databasePath, getBranchName());

        branch = new MessageBranch(messageStorage, identity);
    }
}


public class MessageBranch {
    private MessageBranchInfo messageBranchInfo;
    private List<Message> messages = new ArrayList<>();
    private SecureStorageDir messageStorage;

    private UserIdentity identity;

    public MessageBranch(SecureStorageDir dir, UserIdentity identity) throws IOException, CryptoException {
        messageStorage = dir;
        this.identity = identity;

        loadMessageBranchInfo();
        loadMessages();
    }

    public void setMessageBranchInfo(MessageBranchInfo info) {

        messageBranchInfo = info;
    }

    private void loadMessageBranchInfo() throws IOException, CryptoException {
        byte[] pack = messageStorage.readBytes("i");

        MessageBranchInfo info = new MessageBranchInfo();
        info.load(identity, pack);

        messageBranchInfo = info;
    }

    public void addMessage(Message message) {
        // write message


        messages.add(message);
    }

    private void loadMessages() throws IOException {
        List<String> timeStamps = messageStorage.listDirectories("");

        for (String timeStamp : timeStamps) {
            List<String> firstParts = messageStorage.listDirectories(timeStamp);
            for (String firstPart : firstParts) {
                List<String> secondParts = messageStorage.listDirectories(timeStamp + "/" + firstPart);
                for (String secondPart : secondParts) {
                    String messageId = firstPart + secondPart;


                }
            }
        }

    }

}
