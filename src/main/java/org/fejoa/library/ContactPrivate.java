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
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.database.SecureStorageDir;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ContactPrivate extends Contact {
    private KeyStore keyStore;
    final private Map<String, KeyStore.AsymmetricKeyData> keys = new HashMap<>();


    // create a new contact
    public ContactPrivate(SecureStorageDir storageDir, KeyStore keyStore, KeyId keyId,
                          KeyStore.AsymmetricKeyData keyData) {
        super(storageDir);

        this.uid = keyId.getKeyId();
        this.keyStore = keyStore;

        addAsymmetricKey(keyId, keyData);
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

        storageDir.writeString("keyStoreId", keyStore.getUid());
        for (Map.Entry<String, KeyStore.AsymmetricKeyData> entry : keys.entrySet()) {
            String keyId = entry.getKey();
            storageDir.writeString("keys/" + keyId + "/keyId", keyId);
        }
    }

    private void open(IKeyStoreFinder keyStoreFinder) throws IOException, CryptoException {
        super.open();

        String keyStoreId = storageDir.readString("keyStoreId");
        keyStore = keyStoreFinder.find(keyStoreId);
        if (keyStore == null)
            throw new IOException("key store not found");
        List<String> keyIds = storageDir.listDirectories("keys");
        for (String keyId : keyIds) {
            KeyStore.AsymmetricKeyData data = keyStore.readAsymmetricKey(keyId);
            keys.put(keyId, data);
        }
    }

    public KeyPair getKeyPair(String keyId) {
        if (!keys.containsKey(keyId))
            return null;
        return keys.get(keyId).keyPair;
    }

    public void addAsymmetricKey(String keyId, KeyStore.AsymmetricKeyData keyData) {
        keys.put(keyId, keyData);
    }

    public void addAsymmetricKey(KeyId keyId, KeyStore.AsymmetricKeyData keyData) {
        keys.put(keyId.getKeyId(), keyData);
    }

    public byte[] sign(KeyId keyId, byte data[], CryptoSettings signatureSettings) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        KeyPair keyPair = getKeyPair(keyId.getKeyId());
        if (keyPair == null)
            throw new IllegalArgumentException();
        return crypto.sign(data, keyPair.getPrivate(), signatureSettings);
    }

    @Override
    public boolean verify(KeyId keyId, byte data[], byte signature[], CryptoSettings signatureSettings) throws CryptoException {
        if (!keys.containsKey(keyId.getKeyId()))
            return false;
        KeyStore.AsymmetricKeyData keyData = keys.get(keyId.getKeyId());
        ICryptoInterface crypto = Crypto.get();
        return crypto.verifySignature(data, signature, keyData.keyPair.getPublic(), signatureSettings);
    }

    @Override
    public PublicKey getPublicKey(KeyId keyId) {
        KeyPair keyPair = getKeyPair(keyId.getKeyId());
        if (keyPair == null)
            return null;
        return keyPair.getPublic();
    }
}