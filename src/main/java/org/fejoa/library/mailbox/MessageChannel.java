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


/**
 * MessageChannel is the private part of a MessageBranch.
 *
 * It is stored in the mailbox branch.
 *
 */
public class MessageChannel extends Channel {
    private MessageBranch branch;
    final private Mailbox mailbox;

    // create new
    public MessageChannel(String branchDatabasePath, Mailbox mailbox) throws CryptoException, IOException {
        this.mailbox = mailbox;

        create();

        SecureStorageDir branchStorage = SecureStorageDirBucket.get(branchDatabasePath, getBranchName());
        setBranch(MessageBranch.createNewMessageBranch(branchStorage, mailbox, getParcelCrypto()));
    }

    // load
    public MessageChannel(SecureStorageDir dir, Mailbox mailbox) throws IOException, CryptoException {
        this.mailbox = mailbox;
        load(dir, mailbox);
    }

    public MessageBranch getBranch() {
        return branch;
    }

    private void setBranch(final MessageBranch branch) {
        this.branch = branch;
        this.branch.addListener(new MessageBranch.IListener() {
            @Override
            public void onMessageAdded(Message message) {

            }

            @Override
            public void onCommit() {
                try {
                    onBranchCommit();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void onBranchCommit() throws IOException, CryptoException {
        mailbox.onBranchCommited(this);
    }

    public byte[] sharePack(ContactPrivate sender, KeyId senderKey, Contact receiver, KeyId receiverKey) throws CryptoException, IOException {
        return pack(sender, senderKey, receiver, receiverKey);
    }

    public String shareSignatureKeyPEM() {
        return CryptoHelper.convertToPEM(signatureKeyPublic);
    }

    public void write(SecureStorageDir dir, ContactPrivate sender, KeyId senderKey) throws CryptoException, IOException {
        dir.writeString("signature_key", CryptoHelper.convertToPEM(signatureKeyPublic));
        byte[] pack = pack(sender, senderKey, sender, senderKey);
        dir.writeBytes("d", pack);
        dir.writeString("database_path", dir.getDatabase().getPath());
    }

    private void load(SecureStorageDir dir, Mailbox mailbox)
            throws IOException, CryptoException {
        PublicKey publicKey = CryptoHelper.publicKeyFromPem(dir.readString("signature_key"));
        byte[] data = dir.readBytes("d");
        load(mailbox.getUserIdentity(), publicKey, data);

        String databasePath = dir.readString("database_path");
        SecureStorageDir messageStorage = SecureStorageDirBucket.get(databasePath, getBranchName());

        setBranch(MessageBranch.loadMessageBranch(messageStorage, mailbox, getParcelCrypto()));
    }
}
