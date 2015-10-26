/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.*;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.util.List;


public class ContactStorage extends StorageKeyStore {
    protected ContactStorage(FejoaContext context, StorageDir dir) {
        super(context, dir);
    }

    @Override
    protected void create(KeyStore keyStore, KeyId keyId) throws IOException, CryptoException {
        super.create(keyStore, keyId);
    }

    @Override
    protected void open(List<KeyStore> keyStores) throws IOException, CryptoException {
        super.open(keyStores);
    }
}
