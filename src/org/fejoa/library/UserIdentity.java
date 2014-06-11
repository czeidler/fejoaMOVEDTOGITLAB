/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;


import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.database.IDatabaseInterface;

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
        myself = new ContactPrivate(storageDir, "myself", keyStore);
        myself.addKeyPair(personalKeyId.getKeyId(), personalKey);
        myself.setMainKey(personalKeyId);
        myself.write();

        /*
        error = write(kPathMailboxId, mailbox->getUid());
        if (error != WP::kOk)
            return error;

        QString outPut("signature.pup");
        writePublicSignature("signature.pup", myself.getKey());*/
    }

    public void open(IDatabaseInterface databaseInterface, String baseDir, IKeyStoreFinder keyStoreFinder) throws Exception {
        readUserData(new SecureStorageDir(databaseInterface, baseDir), keyStoreFinder);

    }
}
