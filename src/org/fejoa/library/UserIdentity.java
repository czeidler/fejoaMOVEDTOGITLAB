/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;


import org.fejoa.library.crypto.*;
import org.fejoa.library.database.IDatabaseInterface;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.util.List;


public class UserIdentity extends UserData {
    private ICryptoInterface crypto = Crypto.get();
    private SecureStorageDir storageDir;
    private ContactPrivate myself;
    private List<ContactPublic> allContacts;

    public void createNew(SecureStorageDir storageDir)
            throws Exception {
        KeyPair personalKey = crypto.generateKeyPair(CryptoSettings.ASYMMETRIC_KEY_SIZE);
        byte hashResult[] = CryptoHelper.sha1Hash(personalKey.getPublic().getEncoded());
        uid = CryptoHelper.toHex(hashResult);

        this.storageDir = storageDir;

        writeUserData(uid, storageDir, storageDir.getKeyStore(), storageDir.getKeyId());


        KeyId personalKeyId = storageDir.getKeyStore().writeAsymmetricKey(personalKey);
        myself = new ContactPrivate(personalKeyId, personalKey);
        myself.write(new SecureStorageDir(storageDir, "myself"), storageDir.getKeyStore());

        /*
        error = write(kPathMailboxId, mailbox->getUid());
        if (error != WP::kOk)
            return error;
        */
        writePublicSignature("signature.pup", CryptoHelper.convertToPEM(personalKey.getPublic()));
    }

    public void open(SecureStorageDir dir, IKeyStoreFinder keyStoreFinder)
            throws IOException, CryptoException {
        storageDir = dir;
        readUserData(storageDir, keyStoreFinder);

        myself = new ContactPrivate(new SecureStorageDir(storageDir, "myself"), keyStoreFinder);

        // load contacts
        List<String> contactIds = storageDir.listDirectories("contacts");
        for (String contactId : contactIds) {
            ContactPublic contactPublic = new ContactPublic(new SecureStorageDir(storageDir, "contacts/" + contactId));
            allContacts.add(contactPublic);
        }

    }

    public ContactPrivate getMyself() {
        return myself;
    }

    public String getUid() {
        return uid;
    }

    public SecureStorageDir getStorageDir() {
        return storageDir;
    }

    private void writePublicSignature(String filename, String publicKey) throws IOException {
        File file = new File(filename);
        FileWriter fileWriter = new FileWriter(file);
        try {
            fileWriter.write(publicKey);
        } finally {
            fileWriter.close();
        }
    }

    public void addContact(ContactPublic contact) throws IOException, CryptoException {
        allContacts.add(contact);

        contact.write(new SecureStorageDir(storageDir, "contacts/" + contact.getUid()));
    }
}
