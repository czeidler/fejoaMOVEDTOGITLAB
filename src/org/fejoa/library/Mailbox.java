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

import java.io.IOException;
import java.security.KeyPair;

public class Mailbox extends UserData {
    private ICryptoInterface crypto = Crypto.get();

    public Mailbox() {
        byte hashResult[] = CryptoHelper.sha1Hash(crypto.generateInitializationVector(40));
        uid = CryptoHelper.toHex(hashResult);
    }

    public Mailbox(SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder) throws IOException, CryptoException {
        readUserData(storageDir, keyStoreFinder);
    }

    public void write(SecureStorageDir storageDir) throws IOException, CryptoException {
        writeUserData(uid, storageDir);
    }
}
