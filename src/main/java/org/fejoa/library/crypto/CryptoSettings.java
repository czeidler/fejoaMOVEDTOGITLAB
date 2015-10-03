/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

public class CryptoSettings {
    public class KeyTypeSettings {
        public int keySize = -1;
        public String keyType;
    }

    /**
     * KDF + symmetric master key settings
     */
    public class Password extends KeyTypeSettings {
        // kdf
        public String kdfAlgorithm;
        public int kdfIterations = -1;
        // symmetric key encryption iv
        public int ivSize = -1;
    }

    public class Symmetric extends KeyTypeSettings{
        public String algorithm;
        public int ivSize = -1;
    }

    public class Asymmetric extends KeyTypeSettings {
        public String algorithm;
    }

    public class Signature extends KeyTypeSettings {
        public String algorithm;
    }

    public Password masterPassword = new Password();
    public Asymmetric publicKey = new Asymmetric();
    public Signature signature = new Signature();
    public Symmetric symmetric = new Symmetric();

    private CryptoSettings() {

    }

    static public CryptoSettings getDefault() {
        CryptoSettings cryptoSettings = new CryptoSettings();

        cryptoSettings.publicKey.algorithm = "RSA/NONE/PKCS1PADDING";
        cryptoSettings.publicKey.keyType = "RSA";
        cryptoSettings.publicKey.keySize = 2048;

        cryptoSettings.signature.algorithm = "SHA1withRSA";
        cryptoSettings.signature.keyType = "RSA";
        cryptoSettings.signature.keySize = 512;

        cryptoSettings.symmetric.algorithm = "AES/CTR/PKCS5Padding";
        cryptoSettings.symmetric.keyType = "AES";
        cryptoSettings.symmetric.keySize = 256;
        cryptoSettings.symmetric.ivSize = 16;

        cryptoSettings.masterPassword.kdfAlgorithm = "PBKDF2WithHmacSHA1";
        cryptoSettings.masterPassword.kdfIterations = 20000;
        cryptoSettings.masterPassword.keySize = 256;
        cryptoSettings.masterPassword.ivSize = 16;

        return cryptoSettings;
    }

    static public CryptoSettings getFast() {
        CryptoSettings cryptoSettings = getDefault();
        cryptoSettings.publicKey.keySize = 512;

        cryptoSettings.symmetric.keySize = 128;
        cryptoSettings.symmetric.ivSize = 16;

        cryptoSettings.masterPassword.kdfIterations = 1;

        return cryptoSettings;
    }

    static public CryptoSettings messageChannel() {
        CryptoSettings settings = empty();
        CryptoSettings defaultSettings = getDefault();

        settings.publicKey.algorithm = defaultSettings.publicKey.algorithm;
        settings.publicKey.keyType = defaultSettings.publicKey.keyType;
        settings.publicKey.keySize = defaultSettings.publicKey.keySize;
        settings.signature.algorithm = defaultSettings.signature.algorithm;
        settings.signature.keyType = defaultSettings.signature.keyType;
        settings.signature.keySize = defaultSettings.signature.keySize;
        settings.symmetric.algorithm = defaultSettings.symmetric.keyType;
        settings.symmetric.keyType = defaultSettings.symmetric.keyType;
        settings.symmetric.keySize = defaultSettings.symmetric.keySize;
        settings.symmetric.ivSize = defaultSettings.symmetric.ivSize;
        return settings;
    }

    static public CryptoSettings empty() {
        return new CryptoSettings();
    }

    static public Signature signatureSettings(String algorithm) {
        Signature cryptoSettings = signatureSettings();
        cryptoSettings.algorithm = algorithm;
        return cryptoSettings;
    }

    static public Signature signatureSettings() {
        CryptoSettings settings = empty();
        CryptoSettings defaultSettings = getDefault();

        settings.signature.algorithm = defaultSettings.signature.algorithm;
        settings.signature.keyType = defaultSettings.signature.keyType;
        settings.signature.keySize = defaultSettings.signature.keySize;
        return settings.signature;
    }

    static public Symmetric symmetricSettings(String keyType, String algorithm) {
        CryptoSettings cryptoSettings = empty();
        cryptoSettings.symmetric.keyType = keyType;
        cryptoSettings.symmetric.algorithm = algorithm;
        return cryptoSettings.symmetric;
    }

    static public CryptoSettings symmetricKeyTypeSettings(String keyType) {
        CryptoSettings cryptoSettings = empty();
        cryptoSettings.symmetric.keyType = keyType;
        return cryptoSettings;
    }
}
