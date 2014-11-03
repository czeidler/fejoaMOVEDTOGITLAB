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
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ContactPrivate extends Contact {
    private KeyStore keyStore;
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

        storageDir.writeString("keyStoreId", keyStore.getUid());
        for (Map.Entry<String, KeyPair> entry : keys.entrySet()) {
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
            KeyPair keyPair = keyStore.readAsymmetricKey(keyId);
            keys.put(keyId, keyPair);
        }
    }

    public KeyPair getKeyPair(String keyId) {
        if (!keys.containsKey(keyId))
            return null;
        return keys.get(keyId);
    }

    public void addKeyPair(String keyId, KeyPair keyPair) {
        keys.put(keyId, keyPair);
    }

    public void addKeyPair(KeyId keyId, KeyPair keyPair) {
        keys.put(keyId.getKeyId(), keyPair);
    }

    public byte[] sign(KeyId keyId, byte data[]) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        KeyPair keyPair = getKeyPair(keyId.getKeyId());
        if (keyPair == null)
            throw new IllegalArgumentException();
        return crypto.sign(data, keyPair.getPrivate());
    }

    @Override
    public boolean verify(KeyId keyId, byte data[], byte signature[]) throws CryptoException {
        if (!keys.containsKey(keyId.getKeyId()))
            return false;
        KeyPair keyPair = keys.get(keyId.getKeyId());
        ICryptoInterface crypto = Crypto.get();
        return crypto.verifySignature(data, signature, keyPair.getPublic());
    }

    @Override
    public PublicKey getPublicKey(KeyId keyId) {
        KeyPair keyPair = getKeyPair(keyId.getKeyId());
        if (keyPair == null)
            return null;
        return keyPair.getPublic();
    }
}