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
import org.fejoa.library.mailbox.MessageBranch;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;


public class Mailbox extends UserData {
    public interface Listener {
        public void onBranchAdded(MessageBranch branch);
    }

    final private WeakListenable<Listener> mailboxListeners = new WeakListenable();
    final private ICryptoInterface crypto = Crypto.get();
    final private List<MessageBranch> messageBranches = new ArrayList<>();
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

        loadMessageBranches();
    }

    public void write(SecureStorageDir storageDir) throws IOException, CryptoException {
        writeUserData(uid, storageDir);

        storageDir.writeSecureString("user_identity", userIdentity.getUid());
    }

    private void loadMessageBranches() throws IOException, CryptoException {
        List<String> branches = storageDir.listDirectories("");
        for (String branch : branches) {
            SecureStorageDir branchStorage = new SecureStorageDir(storageDir, branch);
            MessageBranch messageBranch = new MessageBranch();
            messageBranch.load(branchStorage, userIdentity);

            addBranchToList(messageBranch);
        }
    }

    public void addBranch(MessageBranch messageBranch) throws CryptoException, IOException {
        SecureStorageDir dir = new SecureStorageDir(storageDir, messageBranch.getBranchName());

        ContactPrivate myself = userIdentity.getMyself();
        messageBranch.write(dir, myself, myself.getMainKeyId());

        addBranchToList(messageBranch);
    }

    private void addBranchToList(MessageBranch messageBranch) {
        messageBranches.add(messageBranch);
        notifyOnBranchAdded(messageBranch);
    }

    private void notifyOnBranchAdded(MessageBranch branch) {
        for (Listener listener : mailboxListeners.getListeners())
            listener.onBranchAdded(branch);
    }
}
