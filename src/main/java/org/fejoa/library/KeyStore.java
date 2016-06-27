/*
 * Copyright 2014-2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.*;
import org.fejoa.library.database.StorageDir;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;


public class KeyStore {
    final String PATH_MASTER_KEY = "masterKey";
    final String PATH_MASTER_KEY_IV = "masterKeyIV";
    final String PATH_MASTER_PASSWORD_ALGORITHM = "masterPasswordAlgo";
    final String PATH_MASTER_PASSWORD_SALT = "masterPasswordSalt";
    final String PATH_MASTER_PASSWORD_SIZE = "masterPasswordSize";
    final String PATH_MASTER_PASSWORD_ITERATIONS = "masterPasswordIterations";
    final String PATH_SYMMETRIC_ALGORITHM = "symmetricAlgorithm";
    final String PATH_SYMMETRIC_KEY_TYPE = "symmetricKeyType";

    final String PATH_SYMMETRIC_KEY = "symmetricKey";
    final String PATH_SYMMETRIC_IV = "symmetricIV";
    final String PATH_PRIVATE_KEY = "privateKey";
    final String PATH_PUBLIC_KEY = "publicKey";

    final private StorageDir storageDir;

    private ICryptoInterface crypto;
    private CryptoSettings settings;
    private SecretKey masterKey;
    private byte masterKeyIV[];

    public static class SymmetricKeyData {
        public SecretKey key;
        public byte iv[];
        public String keyType;
    }

    public static class AsymmetricKeyData {
        public KeyPair keyPair;

        public AsymmetricKeyData(KeyPair keyPair) {
            this.keyPair = keyPair;
        }
    }

    static public KeyStore create(FejoaContext context, String id, String password) throws IOException,
            CryptoException {
        StorageDir dir = context.getStorage(id);
        KeyStore keyStore = new KeyStore(context, dir);
        keyStore.create(password);
        return keyStore;
    }

    static public KeyStore open(FejoaContext context, StorageDir dir, String password) throws IOException,
            CryptoException {
        KeyStore keyStore = new KeyStore(context, dir);
        keyStore.open(password);
        return keyStore;
    }

    private KeyStore(FejoaContext context, StorageDir storageDir) throws IOException {
        this.storageDir = storageDir;
        crypto = context.getCrypto();

        settings = context.getCryptoSettings();
    }

    public String getId() {
        return storageDir.getBranch();
    }

    public StorageDir getStorageDir() {
        return storageDir;
    }

    public void commit() throws IOException {
        storageDir.commit();
    }

    private boolean open(String password) {
        try {
            byte encryptedMasterKey[] = storageDir.readBytes(PATH_MASTER_KEY);
            masterKeyIV = storageDir.readBytes(PATH_MASTER_KEY_IV);
            String algorithmName = storageDir.readString(PATH_MASTER_PASSWORD_ALGORITHM);
            byte salt[] = storageDir.readBytes(PATH_MASTER_PASSWORD_SALT);
            int masterPasswordSize = storageDir.readInt(PATH_MASTER_PASSWORD_SIZE);
            int masterPasswordIterations = storageDir.readInt(PATH_MASTER_PASSWORD_ITERATIONS);
            String symmetricAlgorithm = storageDir.readString(PATH_SYMMETRIC_ALGORITHM);
            String symmetricKeyType = storageDir.readString(PATH_SYMMETRIC_KEY_TYPE);
            settings.symmetric = CryptoSettings.symmetricSettings(symmetricKeyType, symmetricAlgorithm);

            SecretKey secretKey = crypto.deriveKey(password, salt, algorithmName, masterPasswordIterations,
                    masterPasswordSize);
            byte masterKeyBytes[] = crypto.decryptSymmetric(encryptedMasterKey, secretKey, masterKeyIV,
                    settings.symmetric);
            masterKey = CryptoHelper.symmetricKeyFromRaw(masterKeyBytes, settings.symmetric);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    private void create(String password) throws IOException,
            CryptoException {
        // create
        byte[] salt = crypto.generateSalt();

        SecretKey passwordKey = crypto.deriveKey(password, salt, settings.masterPassword.kdfAlgorithm,
                settings.masterPassword.kdfIterations, settings.masterPassword.keySize);

        masterKeyIV = crypto.generateInitializationVector(settings.masterPassword.ivSize);
        masterKey = crypto.generateSymmetricKey(settings.masterPassword);
        byte[] encryptedMasterKey = crypto.encryptSymmetric(masterKey.getEncoded(), passwordKey, masterKeyIV,
                settings.symmetric);
        // create master password (master password is encrypted
        storageDir.writeBytes(PATH_MASTER_KEY, encryptedMasterKey);
        storageDir.writeBytes(PATH_MASTER_KEY_IV, masterKeyIV);
        storageDir.writeString(PATH_MASTER_PASSWORD_ALGORITHM, settings.masterPassword.kdfAlgorithm);
        storageDir.writeBytes(PATH_MASTER_PASSWORD_SALT, salt);
        storageDir.writeInt(PATH_MASTER_PASSWORD_SIZE, settings.masterPassword.keySize);
        storageDir.writeInt(PATH_MASTER_PASSWORD_ITERATIONS, settings.masterPassword.kdfIterations);
        storageDir.writeString(PATH_SYMMETRIC_ALGORITHM, settings.symmetric.algorithm);
        storageDir.writeString(PATH_SYMMETRIC_KEY_TYPE, settings.symmetric.keyType);
    }

    /**
     * Stores a symmetric key into the key store.
     *
     * @param key the secret key
     * @param iv initialization vector
     * @return key id
     */
    public KeyId writeSymmetricKey(SecretKey key, byte iv[], String keyType) throws CryptoException,
            IOException {
        byte encryptedKey[] = crypto.encryptSymmetric(key.getEncoded(), masterKey, masterKeyIV, settings.symmetric);
        String keyId = CryptoHelper.toHex(CryptoHelper.sha1Hash(encryptedKey));

        try {
            storageDir.writeBytes(keyId + "/" + PATH_SYMMETRIC_KEY, encryptedKey);
            writeSecure(keyId + "/" + PATH_SYMMETRIC_IV, iv);
            writeSecure(keyId + "/" + PATH_SYMMETRIC_KEY_TYPE, keyType.getBytes());
        } catch (Exception e) {
            storageDir.remove(keyId);
            throw e;
        }

        return new KeyId(keyId);
    }

    public SymmetricKeyData readSymmetricKey(KeyId keyId) throws IOException, CryptoException {
        SymmetricKeyData keyData = new SymmetricKeyData();

        keyData.keyType = new String(readSecure(keyId.getKeyId() + "/" + PATH_SYMMETRIC_KEY_TYPE));
        CryptoSettings settings = CryptoSettings.symmetricKeyTypeSettings(keyData.keyType);
        keyData.key = CryptoHelper.symmetricKeyFromRaw(readSecure(keyId.getKeyId() + "/" + PATH_SYMMETRIC_KEY),
                settings.symmetric);
        keyData.iv = readSecure(keyId.getKeyId() + "/" + PATH_SYMMETRIC_IV);

        return  keyData;
    }

    public KeyId writeAsymmetricKey(AsymmetricKeyData keyData) throws IOException, CryptoException {
        String publicKeyPem = CryptoHelper.convertToPEM(keyData.keyPair.getPublic());
        String keyId = CryptoHelper.toHex(CryptoHelper.sha1Hash(publicKeyPem.getBytes()));

        try {
            String privateKeyPem = CryptoHelper.convertToPEM(keyData.keyPair.getPrivate());
            writeSecure(keyId + "/" + PATH_PRIVATE_KEY, privateKeyPem.getBytes());
            storageDir.writeString(keyId + "/" + PATH_PUBLIC_KEY, publicKeyPem);
        } catch (Exception e) {
            storageDir.remove(keyId);
            throw e;
        }

        return new KeyId(keyId);
    }

    private AsymmetricKeyData readAsymmetricKey(String keyId) throws IOException, CryptoException {
        String privateKeyPem = new String(readSecure(keyId + "/" + PATH_PRIVATE_KEY));
        String publicKeyPem = storageDir.readString(keyId + "/" + PATH_PUBLIC_KEY);

        PrivateKey privateKey = CryptoHelper.privateKeyFromPem((privateKeyPem));
        PublicKey publicKey = CryptoHelper.publicKeyFromPem(publicKeyPem);

        return new AsymmetricKeyData(new KeyPair(publicKey, privateKey));
    }

    private byte[] readSecure(String path) throws IOException, CryptoException {
        byte encrypted[] = storageDir.readBytes(path);
        return crypto.decryptSymmetric(encrypted, masterKey, masterKeyIV, settings.symmetric);
    }

    private void writeSecure(String path, byte[] data) throws IOException, CryptoException {
        byte encrypted[] = crypto.encryptSymmetric(data, masterKey, masterKeyIV, settings.symmetric);
        storageDir.writeBytes(path, encrypted);
    }
}
