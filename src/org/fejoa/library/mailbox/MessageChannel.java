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


public class MessageChannel extends Channel {
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
