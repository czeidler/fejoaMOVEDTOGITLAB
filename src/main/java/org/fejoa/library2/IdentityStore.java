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
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.security.KeyPair;
import java.util.List;


public class IdentityStore extends StorageKeyStore {
    final static private String MY_SELF_DIR = "keys";
    static private ContactPrivate myself;

    static public IdentityStore create(FejoaContext context, String id, KeyStore keyStore, KeyId keyId)
            throws IOException, CryptoException {
        StorageDir dir = context.getStorage(id);
        IdentityStore storage = new IdentityStore(context, dir);
        storage.create(keyStore, keyId);
        return storage;
    }

    static public IdentityStore open(FejoaContext context, StorageDir dir, List<KeyStore> keyStores) throws IOException,
            CryptoException {
        IdentityStore storage = new IdentityStore(context, dir);
        storage.open(keyStores);
        return storage;
    }

    protected IdentityStore(FejoaContext context, StorageDir dir) {
        super(context, dir);
    }

    @Override
    protected void create(KeyStore keyStore, KeyId keyId) throws IOException, CryptoException {
        super.create(keyStore, keyId);

        myself = new ContactPrivate(context, new StorageDir(storageDir, MY_SELF_DIR));
    }

    @Override
    protected void open(List<KeyStore> keyStores) throws IOException, CryptoException {
        super.open(keyStores);

        myself = new ContactPrivate(context, new StorageDir(storageDir, MY_SELF_DIR));
    }

    public String addSignatureKeyPair(KeyPair pair, CryptoSettings.KeyTypeSettings keyTypeSettings) throws IOException {
        KeyPairItem keyPairItem = new KeyPairItem(pair, keyTypeSettings);
        myself.addSignatureKey(keyPairItem);
        return keyPairItem.getId();
    }

    public KeyPair getEncryptionKeyPair(KeyId keyId) {
        return myself.getEncryptionKey(keyId).getKeyPair();
    }

    public String addEncryptionKeyPair(KeyPair pair, CryptoSettings.Asymmetric keyTypeSettings) throws IOException {
        KeyPairItem keyPairItem = new KeyPairItem(pair, keyTypeSettings);
        myself.addEncryptionKey(keyPairItem);
        return keyPairItem.getId();
    }

    public void setDefaultEncryptionKey(String defaultEncryptionKey) throws IOException {
        myself.getEncryptionKeys().setDefault(defaultEncryptionKey);
    }

    public void setDefaultSignatureKey(String defaultSignatureKey) throws IOException {
        myself.getSignatureKeys().setDefault(defaultSignatureKey);
    }

    public String getDefaultSignatureKey() {
        return myself.getSignatureKeys().getDefault().getId();
    }

    public String getDefaultEncryptionKey() {
        return myself.getEncryptionKeys().getDefault().getId();
    }
}

