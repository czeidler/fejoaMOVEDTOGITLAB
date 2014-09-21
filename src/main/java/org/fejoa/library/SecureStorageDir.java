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
import org.fejoa.library.database.IDatabaseInterface;

import java.io.IOException;


public class SecureStorageDir extends StorageDir {
    private KeyStore keyStore;
    private KeyId keyId;
    private KeyStore.SecreteKeyIVPair secreteKeyIVPair;
    private ICryptoInterface crypto = Crypto.get();

    public SecureStorageDir(SecureStorageDir storageDir, String baseDir) {
        super(storageDir, baseDir, false);

        keyStore = storageDir.keyStore;
        keyId = storageDir.keyId;
        secreteKeyIVPair = storageDir.secreteKeyIVPair;
    }

    public SecureStorageDir(SecureStorageDir storageDir, String baseDir, boolean absoluteBaseDir) {
        super(storageDir, baseDir, absoluteBaseDir);

        keyStore = storageDir.keyStore;
        keyId = storageDir.keyId;
        secreteKeyIVPair = storageDir.secreteKeyIVPair;
    }

    public SecureStorageDir(IDatabaseInterface database, String baseDir) {
        super(database, baseDir);
    }

    public void setTo(KeyStore keyStore, KeyId keyId) throws IOException, CryptoException {
        this.keyStore = keyStore;
        this.keyId = keyId;
        secreteKeyIVPair = keyStore.readSymmetricKey(keyId);
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public KeyId getKeyId() {
        return keyId;
    }

    public byte[] readSecureBytes(String path) throws IOException, CryptoException {
        byte encrypted[] = readBytes(path);
        return crypto.decryptSymmetric(encrypted, secreteKeyIVPair.key, secreteKeyIVPair.iv);
    }
    public String readSecureString(String path) throws IOException, CryptoException {
        return new String(readSecureBytes(path));
    }

    public void writeSecureBytes(String path, byte[] data) throws IOException, CryptoException {
        byte encrypted[] = crypto.encryptSymmetric(data, secreteKeyIVPair.key, secreteKeyIVPair.iv);
        writeBytes(path, encrypted);
    }

    public void writeSecureString(String path, String data) throws IOException, CryptoException {
        writeSecureBytes(path, data.getBytes());
    }
}
