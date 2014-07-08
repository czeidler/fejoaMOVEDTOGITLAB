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

    public void createNew(IDatabaseInterface databaseInterface, final String baseDir, KeyStore keyStore, KeyId keyId)
            throws Exception {
        KeyPair personalKey = crypto.generateKeyPair(CryptoSettings.ASYMMETRIC_KEY_SIZE);
        byte hashResult[] = CryptoHelper.sha1Hash(personalKey.getPublic().getEncoded());
        uid = CryptoHelper.toHex(hashResult);

        storageDir = new SecureStorageDir(databaseInterface, StorageDir.appendDir(baseDir, uid));

        writeUserData(uid, storageDir, keyStore, keyId);


        KeyId personalKeyId = keyStore.writeAsymmetricKey(personalKey);
        myself = new ContactPrivate(storageDir, "myself");
        myself.addKeyPair(personalKeyId.getKeyId(), personalKey);
        myself.setMainKey(personalKeyId);
        myself.write(keyStore);

        /*
        error = write(kPathMailboxId, mailbox->getUid());
        if (error != WP::kOk)
            return error;
        */
        writePublicSignature("signature.pup", CryptoHelper.convertToPEM(personalKey.getPublic()));
    }

    public void open(IDatabaseInterface databaseInterface, String baseDir, IKeyStoreFinder keyStoreFinder)
            throws IOException, CryptoException {
        storageDir = new SecureStorageDir(databaseInterface, baseDir);
        readUserData(storageDir, keyStoreFinder);

        myself = new ContactPrivate(storageDir, "myself");
        myself.open(keyStoreFinder);
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
}
