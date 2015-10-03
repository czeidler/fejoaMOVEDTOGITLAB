/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.KeyId;
import org.fejoa.library.KeyStore;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;

import java.io.IOException;


public class SecureStorageDir extends StorageDir {
    private KeyStore keyStore;
    private KeyId keyId;
    private KeyStore.SymmetricKeyData symmetricKeyData;
    private ICryptoInterface crypto = Crypto.get();
    private CryptoSettings.Symmetric cryptoSettings;

    static public SecureStorageDir createStorage(String path, String branch, KeyStore keyStore, KeyId keyId,
                                                 String symAlgorithm) throws IOException, CryptoException {
        IDatabaseInterface databaseInterface = DatabaseFactory.getDatabaseFor(path, branch);
        SecureStorageDir dir = new SecureStorageDir(databaseInterface, "");
        dir.setTo(keyStore, keyId, symAlgorithm);
        return dir;
    }

    public SecureStorageDir(SecureStorageDir storageDir, String baseDir) {
        super(storageDir, baseDir, false);

        keyStore = storageDir.keyStore;
        keyId = storageDir.keyId;
        this.symmetricKeyData = storageDir.symmetricKeyData;
        this.cryptoSettings = storageDir.cryptoSettings;
    }

    public SecureStorageDir(SecureStorageDir storageDir, String baseDir, boolean absoluteBaseDir) {
        super(storageDir, baseDir, absoluteBaseDir);

        keyStore = storageDir.keyStore;
        keyId = storageDir.keyId;
        this.symmetricKeyData = storageDir.symmetricKeyData;
        this.cryptoSettings = storageDir.cryptoSettings;
    }

    public SecureStorageDir(IDatabaseInterface database, String baseDir) {
        super(database, baseDir);
    }

    public void setTo(KeyStore keyStore, KeyId keyId, String algorithm) throws IOException, CryptoException {
        this.keyStore = keyStore;
        this.keyId = keyId;
        this.symmetricKeyData = keyStore.readSymmetricKey(keyId);
        cryptoSettings = CryptoSettings.symmetricSettings(symmetricKeyData.keyType, algorithm);
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public KeyId getKeyId() {
        return keyId;
    }

    public String getSymmetricAlgorithm() {
        return cryptoSettings.algorithm;
    }

    public byte[] readSecureBytes(String path) throws IOException, CryptoException {
        byte encrypted[] = readBytes(path);
        return crypto.decryptSymmetric(encrypted, symmetricKeyData.key, symmetricKeyData.iv, cryptoSettings);
    }
    public String readSecureString(String path) throws IOException, CryptoException {
        return new String(readSecureBytes(path));
    }

    public void writeSecureBytes(String path, byte[] data) throws IOException, CryptoException {
        byte encrypted[] = crypto.encryptSymmetric(data, symmetricKeyData.key, symmetricKeyData.iv,
                cryptoSettings);
        writeBytes(path, encrypted);
    }

    public void writeSecureString(String path, String data) throws IOException, CryptoException {
        writeSecureBytes(path, data.getBytes());
    }
}
