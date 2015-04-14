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
import org.fejoa.library.database.FejoaEnvironment;
import org.fejoa.library.database.SecureStorageDir;
import org.fejoa.library.database.StorageDir;

import java.io.IOException;
import java.security.PublicKey;


/**
 * MessageChannel is the private part of a MessageBranch.
 *
 * It is stored in the mailbox branch.
 *
 */
public class MessageChannel extends Channel {
    final FejoaEnvironment environment;
    private MessageBranch branch;
    final private Mailbox mailbox;

    // create new
    public MessageChannel(FejoaEnvironment environment, String branchDatabasePath, Mailbox mailbox)
            throws CryptoException, IOException {
        this.environment = environment;
        this.mailbox = mailbox;

        create();

        SecureStorageDir branchStorage = environment.get(branchDatabasePath, getBranchName());
        setBranch(MessageBranch.createNewMessageBranch(branchStorage, mailbox, getParcelCrypto()));
    }

    // load
    public MessageChannel(FejoaEnvironment environment, SecureStorageDir dir, Mailbox mailbox) throws IOException,
            CryptoException {
        this.environment = environment;
        this.mailbox = mailbox;
        load(dir, mailbox);
    }

    public Mailbox getMailbox() {
        return mailbox;
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
        mailbox.onBranchCommitted(this);
    }

    public byte[] sharePack(ContactPrivate sender, KeyId senderKey, Contact receiver, KeyId receiverKey) throws CryptoException, IOException {
        return pack(sender, senderKey, receiver, receiverKey);
    }

    public String shareSignatureKeyPEM() {
        return CryptoHelper.convertToPEM(signatureKeyPublic);
    }

    public void write(SecureStorageDir dir, ContactPrivate sender, KeyId senderKey) throws CryptoException, IOException {
        dir.writeString("signatureKey", CryptoHelper.convertToPEM(signatureKeyPublic));
        byte[] pack = pack(sender, senderKey, sender, senderKey);
        dir.writeBytes("d", pack);
        dir.writeString("branchTip", getBranch().getTip());
    }

    private void load(SecureStorageDir dir, Mailbox mailbox) throws IOException, CryptoException {
        PublicKey publicKey = CryptoHelper.publicKeyFromPem(dir.readString("signatureKey"));
        byte[] data = dir.readBytes("d");
        load(mailbox.getUserIdentity(), publicKey, data);
        SecureStorageDir messageStorage = environment.getChannelBranchStorage(getBranchName());

        // try to load the message branch but don't fail if it is not there
        try {
            MessageBranch messageBranch = MessageBranch.loadMessageBranch(messageStorage, mailbox, getParcelCrypto());
            setBranch(messageBranch);
        } catch (Exception e) {
            return;
        }
    }

    public StorageDir getMessageStorage() throws IOException {
        if (branch != null)
            return branch.getMessageStorage();

        return environment.get(mailbox.getMessageStoragePath(), getBranchName());
    }
}
