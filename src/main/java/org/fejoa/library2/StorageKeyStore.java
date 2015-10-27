/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library2.database.SecureIOFilter;
import org.fejoa.library2.database.StorageDir;
import org.fejoa.library2.util.CryptoSettingsIO;

import java.io.IOException;
import java.util.List;


public class StorageKeyStore {
    final static public String KEY_STORES_ID_KEY = "keyStoreId";
    final static private String KEY_ID_KEY = "keyId";
    final static private String CRYPTO_SETTINGS_PREFIX = "crypto";

    final protected FejoaContext context;
    final protected StorageDir storageDir;

    protected KeyStore keyStore;
    protected KeyId keyId;

    protected StorageKeyStore(FejoaContext context, StorageDir dir) {
        this.context = context;
        this.storageDir = dir;
    }

    protected void create(KeyStore keyStore, KeyId keyId) throws IOException, CryptoException {
        this.keyStore = keyStore;
        this.keyId = keyId;

        CryptoSettings cryptoSettings = context.getCryptoSettings();
        CryptoSettings.Symmetric symmetricSettings = cryptoSettings.symmetric;

        storageDir.writeString(KEY_STORES_ID_KEY, keyStore.getId());
        storageDir.writeString(KEY_ID_KEY, keyId.toString());
        CryptoSettingsIO.write(symmetricSettings, storageDir, CRYPTO_SETTINGS_PREFIX);
        storageDir.setFilter(new SecureIOFilter(context.getCrypto(), keyStore.readSymmetricKey(keyId),
                symmetricSettings));
    }

    protected void open(List<KeyStore> keyStores) throws IOException, CryptoException {
        // setup encryption
        String keyStoreId = storageDir.readString(KEY_STORES_ID_KEY);
        for (KeyStore keyStore : keyStores) {
            if (keyStore.getId().equals(keyStoreId)) {
                this.keyStore = keyStore;
                break;
            }
        }
        keyId = new KeyId(storageDir.readString(KEY_ID_KEY));

        CryptoSettings.Symmetric symmetricSettings = CryptoSettings.getDefault().symmetric;
        CryptoSettingsIO.read(symmetricSettings, storageDir, CRYPTO_SETTINGS_PREFIX);
        storageDir.setFilter(new SecureIOFilter(context.getCrypto(), keyStore.readSymmetricKey(keyId),
                symmetricSettings));
    }

    public void commit() throws IOException {
        storageDir.commit();
    }

    public String getId() {
        return storageDir.getBranch();
    }
}
