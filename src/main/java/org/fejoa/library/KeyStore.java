/*
 * Copyright 2014.
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

    final private String PATH_MASTER_KEY = "master_key";
    final private String PATH_MASTER_KEY_IV = "master_key_iv";
    final private String PATH_MASTER_PASSWORD_ALGORITHM = "master_password_algo";
    final private String PATH_MASTER_PASSWORD_SALT = "master_password_salt";
    final private String PATH_MASTER_PASSWORD_SIZE = "master_password_size";
    final private String PATH_MASTER_PASSWORD_ITERATIONS = "master_password_iterations";

    final private String PATH_SYMMETRIC_KEY = "symmetric_key";
    final private String PATH_SYMMETRIC_IV = "symmetric_iv";
    final private String PATH_PRIVATE_KEY = "private_key";
    final private String PATH_PUBLIC_KEY = "public_key";

    private String uid;
    private StorageDir storageDir;

    private ICryptoInterface crypto;
    private SecretKey masterKey;
    private byte salt[];
    private byte masterKeyIV[];
    private byte encryptedMasterKey[];

    public KeyStore(StorageDir storageDir) throws IOException {
        this.storageDir = storageDir;
        crypto = Crypto.get();

        uid = storageDir.readString("uid");
    }

    public KeyStore(String password) {
        crypto = Crypto.get();

        salt = crypto.generateSalt();
        try {
            SecretKey passwordKey = crypto.deriveKey(password, salt, CryptoSettings.KDF_ALGORITHM,
                    CryptoSettings.MASTER_PASSWORD_ITERATIONS, CryptoSettings.MASTER_PASSWORD_LENGTH);

            masterKeyIV = crypto.generateInitializationVector(CryptoSettings.MASTER_PASSWORD_IV_LENGTH);
            masterKey = crypto.generateSymmetricKey(CryptoSettings.MASTER_PASSWORD_LENGTH);
            encryptedMasterKey = crypto.encryptSymmetric(masterKey.getEncoded(), passwordKey, masterKeyIV);

            makeUidFromEncryptedMasterKey(encryptedMasterKey);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public String getUid() {
        return uid;
    }

    public StorageDir getStorageDir() {
        return storageDir;
    }

    private void makeUidFromEncryptedMasterKey(byte encryptedMasterKey[]) {
        uid = CryptoHelper.toHex(CryptoHelper.sha1Hash(encryptedMasterKey));
    }

    public boolean open(String password) {
        try {
            byte encryptedMasterKey[] = storageDir.readBytes(PATH_MASTER_KEY);
            byte masterKeyIV[] = storageDir.readBytes(PATH_MASTER_KEY_IV);
            String algorithmName = storageDir.readString(PATH_MASTER_PASSWORD_ALGORITHM);
            byte salt[] = storageDir.readBytes(PATH_MASTER_PASSWORD_SALT);
            int masterPasswordSize = storageDir.readInt(PATH_MASTER_PASSWORD_SIZE);
            int masterPasswordIterations = storageDir.readInt(PATH_MASTER_PASSWORD_ITERATIONS);

            SecretKey secretKey = crypto.deriveKey(password, salt, algorithmName, masterPasswordIterations,
                    masterPasswordSize);
            byte masterKeyBytes[] = crypto.decryptSymmetric(encryptedMasterKey, secretKey, masterKeyIV);
            masterKey = CryptoHelper.symmetricKeyFromRaw(masterKeyBytes);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }


    public void create(StorageDir storageDir) throws IOException {
        if (uid == null)
            throw new IllegalStateException();
        this.storageDir = storageDir;

        storageDir.writeString("uid", uid);

        // write master password (master password is encrypted
        storageDir.writeBytes(PATH_MASTER_KEY, encryptedMasterKey);
        storageDir.writeBytes(PATH_MASTER_KEY_IV, masterKeyIV);
        storageDir.writeString(PATH_MASTER_PASSWORD_ALGORITHM, CryptoSettings.KDF_ALGORITHM);
        storageDir.writeBytes(PATH_MASTER_PASSWORD_SALT, salt);
        storageDir.writeInt(PATH_MASTER_PASSWORD_SIZE, CryptoSettings.MASTER_PASSWORD_LENGTH);
        storageDir.writeInt(PATH_MASTER_PASSWORD_ITERATIONS, CryptoSettings.MASTER_PASSWORD_ITERATIONS);

        // free memory
        encryptedMasterKey = null;
        salt = null;
    }

    /**
     * Stores a symmetric key into the key store.
     *
     * @param key the secret key
     * @param iv initialization vector
     * @return key id
     */
    public KeyId writeSymmetricKey(SecretKey key, byte iv[]) throws CryptoException, IOException {
        byte encryptedKey[] = crypto.encryptSymmetric(key.getEncoded(), masterKey, masterKeyIV);
        String keyId = CryptoHelper.toHex(CryptoHelper.sha1Hash(encryptedKey));

        String path = keyId + "/" + PATH_SYMMETRIC_KEY;
        storageDir.writeBytes(path, encryptedKey);

        path = keyId + "/" + PATH_SYMMETRIC_IV;
        try {
            storageDir.writeBytes(path, iv);
        } catch (Exception e) {
            storageDir.remove(keyId);
            throw e;
        }

        return new KeyId(keyId);
    }

    public class SecreteKeyIVPair {
        public SecretKey key;
        public byte iv[];
    }

    public SecreteKeyIVPair readSymmetricKey(KeyId keyId) throws IOException, CryptoException {
        SecreteKeyIVPair pair = new SecreteKeyIVPair();

        String path = keyId.getKeyId() + "/" + PATH_SYMMETRIC_KEY;
        byte encryptedKey[] = storageDir.readBytes(path);
        path = keyId.getKeyId() + "/" + PATH_SYMMETRIC_IV;
        masterKeyIV = storageDir.readBytes(path);
        pair.key = CryptoHelper.symmetricKeyFromRaw(crypto.decryptSymmetric(encryptedKey, masterKey, masterKeyIV));
        pair.iv = masterKeyIV;

        return  pair;
    }

    public KeyId writeAsymmetricKey(KeyPair keyPair) throws IOException, CryptoException {
        String privateKeyPem = CryptoHelper.convertToPEM(keyPair.getPrivate());
        byte encryptedPrivate[] = crypto.encryptSymmetric(privateKeyPem.getBytes(), masterKey, masterKeyIV);

        String keyId = CryptoHelper.toHex(CryptoHelper.sha1Hash(keyPair.getPublic().getEncoded()));
        String path = keyId + "/" + PATH_PRIVATE_KEY;
        storageDir.writeBytes(path, encryptedPrivate);
        path = keyId + "/" + PATH_PUBLIC_KEY;

        try {
            String publicKeyPem = CryptoHelper.convertToPEM(keyPair.getPublic());
            storageDir.writeString(path, publicKeyPem);
        } catch (Exception e) {
            storageDir.remove(keyId);
            throw e;
        }

        return new KeyId(keyId);
    }

    KeyPair readAsymmetricKey(String keyId) throws IOException, CryptoException {
        String path = keyId + "/" + PATH_PRIVATE_KEY;
        byte encryptedPrivate[] = storageDir.readBytes(path);
        path = keyId + "/" + PATH_PUBLIC_KEY;
        String publicKeyPem = storageDir.readString(path);
        PublicKey publicKey = CryptoHelper.publicKeyFromPem(publicKeyPem);
        byte decryptedPrivate[] = crypto.decryptSymmetric(encryptedPrivate, masterKey, masterKeyIV);

        PrivateKey privateKey = CryptoHelper.privateKeyFromPem(new String(decryptedPrivate));
        return new KeyPair(publicKey, privateKey);
    }

}
