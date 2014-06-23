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
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.ICryptoInterface;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ContactPrivate {
    private String uid;
    final private SecureStorageDir storageDir;
    private KeyId mainKeyId;
    final private Map<String, KeyPair> keys = new HashMap<>();

    public ContactPrivate(SecureStorageDir storageDir, String uid) {
        this.uid = uid;
        String baseDir = "";
        baseDir += storageDir.getBaseDir();
        if (!baseDir.equals(""))
            baseDir += "/";
        baseDir += uid;
        this.storageDir = new SecureStorageDir(storageDir, baseDir);
    }

    public String getUid() {
        return uid;
    }

    public void write(KeyStore keyStore) throws Exception {
        storageDir.writeString("keyStoreId", keyStore.getUid());
        storageDir.writeString("main_key_id", mainKeyId.getKeyId());
        for (Map.Entry<String, KeyPair> entry : keys.entrySet()) {
            String keyId = entry.getKey();
            storageDir.writeString(keyId + "/keyId", keyId);
        }
    }

    public void open(IKeyStoreFinder keyStoreFinder) throws IOException, CryptoException {
        String keyStoreId = storageDir.readString("keyStoreId");
        KeyStore keyStore = keyStoreFinder.find(keyStoreId);
        if (keyStore == null)
            throw new IOException("key store not found");
        mainKeyId = new KeyId(storageDir.readString("main_key_id"));
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