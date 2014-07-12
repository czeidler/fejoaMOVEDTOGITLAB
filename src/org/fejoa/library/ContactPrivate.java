/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.ICryptoInterface;

import java.io.IOException;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ContactPrivate extends Contact {
    private KeyStore keyStore;
    private KeyId mainKeyId;
    final private Map<String, KeyPair> keys = new HashMap<>();


    // create a new contact
    public ContactPrivate(SecureStorageDir storageDir, KeyStore keyStore, KeyId keyId, KeyPair keyPair) {
        super(storageDir);

        this.uid = keyId.getKeyId();
        this.keyStore = keyStore;

        addKeyPair(keyId, keyPair);
        setMainKey(keyId);
    }

    public ContactPrivate(SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder) throws IOException,
            CryptoException {
        super(storageDir);

        open(keyStoreFinder);
    }

    public String getUid() {
        return uid;
    }

    @Override
    public void write() throws IOException, CryptoException {
        super.write();

        storageDir.writeSecureString("keyStoreId", keyStore.getUid());
        storageDir.writeSecureString("main_key_id", mainKeyId.getKeyId());
        for (Map.Entry<String, KeyPair> entry : keys.entrySet()) {
            String keyId = entry.getKey();
            storageDir.writeString(keyId + "/keyId", keyId);
        }
    }

    private void open(IKeyStoreFinder keyStoreFinder) throws IOException, CryptoException {
        super.open();

        String keyStoreId = storageDir.readSecureString("keyStoreId");
        keyStore = keyStoreFinder.find(keyStoreId);
        if (keyStore == null)
            throw new IOException("key store not found");
        mainKeyId = new KeyId(storageDir.readSecureString("main_key_id"));
        List<String> keyIds = storageDir.listDirectories("");
        for (String keyId : keyIds) {
            KeyPair keyPair = keyStore.readAsymmetricKey(keyId);
            keys.put(keyId, keyPair);
        }
    }

    public KeyPair getKeyPair(String keyId) {
        return keys.get(keyId);
    }

    public void addKeyPair(String keyId, KeyPair keyPair) {
        keys.put(keyId, keyPair);
    }

    public void addKeyPair(KeyId keyId, KeyPair keyPair) {
        keys.put(keyId.getKeyId(), keyPair);
    }

    public void setMainKey(KeyId keyId) {
        mainKeyId = keyId;
    }
    public KeyId getMainKeyId() {
        return mainKeyId;
    }

    public byte[] sign(KeyId keyId, byte data[]) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        KeyPair keyPair = getKeyPair(keyId.getKeyId());
        if (keyPair == null)
            throw new IllegalArgumentException();
        return crypto.sign(data, keyPair.getPrivate());
    }
}