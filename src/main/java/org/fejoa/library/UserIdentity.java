/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;


import org.fejoa.library.crypto.*;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.DatabaseDir;
import org.fejoa.library.database.SecureStorageDir;
import org.fejoa.library.database.StorageDir;

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

    private StorageDir.IListener storageListener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff, String base, String tip) {
            DatabaseDir baseDir = diff.added.findDirectory(storageDir.getBaseDir());
            if (baseDir == null)
                return;
            DatabaseDir contactBaseDir = baseDir.findDirectory("contacts");
            for (DatabaseDir contactDir : contactBaseDir.getDirectories()) {
                String contactId = contactDir.getDirName();
                ContactPublic contactPublic;
                try {
                    contactPublic = new ContactPublic(new SecureStorageDir(storageDir, "contacts/" + contactId));
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                allContacts.add(contactPublic);
            }
        }
    };

    public void create(SecureStorageDir storageDir, CryptoSettings settings) throws IOException, CryptoException {
        KeyPair personalKey = crypto.generateKeyPair(settings.publicKeySettings);
        byte hashResult[] = CryptoHelper.sha1Hash(personalKey.getPublic().getEncoded());
        uid = CryptoHelper.toHex(hashResult);

        this.storageDir = storageDir;

        writeUserData(uid, storageDir);

        KeyStore.AsymmetricKeyData keyData = new KeyStore.AsymmetricKeyData(personalKey);
        KeyId personalKeyId = storageDir.getKeyStore().writeAsymmetricKey(keyData);
        myself = new ContactPrivate(new SecureStorageDir(storageDir, "myself"), storageDir.getKeyStore(), personalKeyId,
                keyData);
        myself.write();
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

        storageDir.addListener(storageListener);
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

            @Override
            public Contact findByAddress(String address) {
                if (myself.getAddress().equals(address))
                    return myself;
                for (Contact contact : allContacts) {
                    if (contact.getAddress().equals(address))
                        return contact;
                }
                return null;
            }
        };
    }

    public static void writePublicSignature(File file, UserIdentity userIdentity) throws IOException {
        Contact myself = userIdentity.getMyself();
        String publicKey = CryptoHelper.convertToPEM(myself.getPublicKey(myself.getMainKeyId()));

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
