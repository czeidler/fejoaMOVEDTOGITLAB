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
import org.fejoa.library.crypto.ICryptoInterface;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


class ContactPublic {
    final String uid;
    final protected SecureStorageDir storageDir;
    protected Map<String, KeyPair> keys = new HashMap<>();

    public ContactPublic(SecureStorageDir storageDir, String uid) {
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

    public void write() throws Exception {
        for (Map.Entry<String, KeyPair> entry : keys.entrySet()) {
            KeyPair keyPair = entry.getValue();
            String keyId = entry.getKey();
            storageDir.writeString(keyId + "/public_key", CryptoHelper.convertToPEM(keyPair.getPublic()));
        }
    }

    public void read() throws Exception {
        List<String> keyIds = storageDir.listDirectories("");
        for (String keyId : keyIds) {
            PublicKey publicKey = CryptoHelper.publicKeyFromPem(storageDir.readString(keyId + "/public_key"));
            keys.put(keyId, new KeyPair(publicKey, null));
        }
    }

    public PublicKey getKey(String keyId) {
        return keys.get(keyId).getPublic();
    }

    public void addKey(String keyId, PublicKey publicKey) {
        keys.put(keyId, new KeyPair(publicKey, null));
    }

    public boolean verify(KeyId keyId, byte data[], byte signature[]) throws Exception {
        ICryptoInterface crypto = Crypto.get();
        PublicKey publicKey = getKey(keyId.getKeyId());
        return crypto.verifySignature(data, signature, publicKey);
    }
}


class ContactPrivate {
    private String uid;
    final private SecureStorageDir storageDir;
    private KeyStore keyStore;
    private KeyId mainKeyId;
    final private Map<String, KeyPair> keys = new HashMap<>();

    public ContactPrivate(SecureStorageDir storageDir, String uid, KeyStore keyStore) {
        this.uid = uid;
        String baseDir = "";
        baseDir += storageDir.getBaseDir();
        if (!baseDir.equals(""))
            baseDir += "/";
        baseDir += uid;
        this.storageDir = new SecureStorageDir(storageDir, baseDir);
        this.keyStore = keyStore;
    }

    public String getUid() {
        return uid;
    }

    public void write() throws Exception {
        storageDir.writeString("keyStoreId", keyStore.getUid());
        for (Map.Entry<String, KeyPair> entry : keys.entrySet()) {
            String keyId = entry.getKey();
            storageDir.writeString(keyId + "/keyId", keyId);
        }
    }

    public void open(IKeyStoreFinder keyStoreFinder) throws Exception {
        String keyStoreId = storageDir.readString("keyStoreId");
        keyStore = keyStoreFinder.find(keyStoreId);
        if (keyStore == null)
            throw new Exception("key store not found");
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

    public byte[] sign(KeyId keyId, byte data[]) throws Exception {
        ICryptoInterface crypto = Crypto.get();
        KeyPair keyPair = getKeyPair(keyId.getKeyId());
        return crypto.sign(data, keyPair.getPrivate());
    }
}