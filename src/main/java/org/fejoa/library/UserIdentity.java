/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;


import org.fejoa.library.crypto.*;
import org.fejoa.library.database.SecureStorageDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;


public class UserIdentity extends UserData {
    private ICryptoInterface crypto = Crypto.get();
    private ContactPrivate myself;
    private List<ContactPublic> allContacts = new ArrayList<>();

    public void write(SecureStorageDir storageDir) throws IOException, CryptoException {
        KeyPair personalKey = crypto.generateKeyPair(CryptoSettings.ASYMMETRIC_KEY_SIZE);
        byte hashResult[] = CryptoHelper.sha1Hash(personalKey.getPublic().getEncoded());
        uid = CryptoHelper.toHex(hashResult);

        this.storageDir = storageDir;

        writeUserData(uid, storageDir);


        KeyId personalKeyId = storageDir.getKeyStore().writeAsymmetricKey(personalKey);
        myself = new ContactPrivate(new SecureStorageDir(storageDir, "myself"), storageDir.getKeyStore(), personalKeyId,
                personalKey);
        myself.write();

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

    public IContactFinder getContactFinder() {
        return new IContactFinder() {
            @Override
            public Contact find(String uid) {
                if (myself.getUid().equals(uid))
                    return myself;
                for (Contact contact : allContacts) {
                    if (contact.getUid().equals(uid))
                        return contact;
                }
                return null;
            }
        };
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

    public ContactPublic addNewContact(String uid) throws IOException, CryptoException {
        ContactPublic contact = new ContactPublic(new SecureStorageDir(storageDir, "contacts/" + uid), uid);
        allContacts.add(contact);

        return contact;
    }
}
