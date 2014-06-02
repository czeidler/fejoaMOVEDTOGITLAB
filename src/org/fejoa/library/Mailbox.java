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

public class Mailbox extends UserData {
    private ICryptoInterface crypto = Crypto.get();

    public Mailbox() {

    }

    public void create(IDatabaseInterface databaseInterface, final String baseDir, KeyStore keyStore, KeyId keyId)
            throws Exception {
        byte hashResult[] = CryptoHelper.sha1Hash(crypto.generateInitializationVector(40));
        uid = CryptoHelper.toHex(hashResult);

        storageDir = new SecureStorageDir(databaseInterface, baseDir);
        storageDir.appendDir(uid);
        storageDir.setTo(keyStore, keyId);

        writeUserData();
    }

    public void open(IDatabaseInterface databaseInterface, final String baseDir, IKeyStoreFinder keyStoreFinder)
            throws Exception {
        readUserData(new SecureStorageDir(databaseInterface, baseDir), keyStoreFinder);
    }
}
