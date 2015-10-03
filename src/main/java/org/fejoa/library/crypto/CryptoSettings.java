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
    public class PasswordSettings extends KeyTypeSettings {
        public String algorithm;
        public int iterations = -1;
        public int ivSize = -1;
    }

    public class SymmetricSettings extends KeyTypeSettings{
        public String algorithm;
        public int ivSize = -1;
    }

    public class AsymmetricSettings extends KeyTypeSettings {
        public String algorithm;
    }

    public class SignatureSettings {
        public String algorithm;
    }

    public PasswordSettings masterPassword = new PasswordSettings();
    public AsymmetricSettings publicKeySettings = new AsymmetricSettings();
    public AsymmetricSettings signatureSettings = new AsymmetricSettings();

    public SymmetricSettings symmetric = new SymmetricSettings();
    public SignatureSettings signature = new SignatureSettings();

    private CryptoSettings() {

    }

    static public CryptoSettings getDefault() {
        CryptoSettings cryptoSettings = new CryptoSettings();

        cryptoSettings.publicKeySettings.algorithm = "RSA/NONE/PKCS1PADDING";
        cryptoSettings.publicKeySettings.keyType = "RSA";
        cryptoSettings.publicKeySettings.keySize = 2048;

        cryptoSettings.signatureSettings.algorithm = "RSA/NONE/PKCS1PADDING";
        cryptoSettings.signatureSettings.keyType = "RSA";
        cryptoSettings.signatureSettings.keySize = 512;

        cryptoSettings.symmetric.algorithm = "AES/CBC/PKCS7Padding";
        cryptoSettings.symmetric.keyType = "AES";
        cryptoSettings.symmetric.keySize = 256;
        cryptoSettings.symmetric.ivSize = 16;

        cryptoSettings.signature.algorithm = "SHA1withRSA";

        cryptoSettings.masterPassword.algorithm = "PBKDF2WithHmacSHA1";
        cryptoSettings.masterPassword.iterations = 20000;
        cryptoSettings.masterPassword.keySize = 256;
        cryptoSettings.masterPassword.ivSize = 16;

        return cryptoSettings;
    }

    static public CryptoSettings getFast() {
        CryptoSettings cryptoSettings = getDefault();
        cryptoSettings.publicKeySettings.keySize = 512;

        cryptoSettings.symmetric.keySize = 128;
        cryptoSettings.symmetric.ivSize = 16;

        cryptoSettings.masterPassword.iterations = 1;

        return cryptoSettings;
    }

    static public CryptoSettings messageChannel() {
        CryptoSettings settings = empty();
        CryptoSettings defaultSettings = getDefault();

        settings.publicKeySettings.algorithm = defaultSettings.publicKeySettings.algorithm;
        settings.publicKeySettings.keyType = defaultSettings.publicKeySettings.keyType;
        settings.publicKeySettings.keySize = defaultSettings.publicKeySettings.keySize;
        settings.signatureSettings.algorithm = defaultSettings.signatureSettings.algorithm;
        settings.signatureSettings.keyType = defaultSettings.signatureSettings.keyType;
        settings.signatureSettings.keySize = defaultSettings.signatureSettings.keySize;
        settings.symmetric.algorithm = defaultSettings.symmetric.keyType;
        settings.symmetric.keyType = defaultSettings.symmetric.keyType;
        settings.symmetric.keySize = defaultSettings.symmetric.keySize;
        settings.symmetric.ivSize = defaultSettings.symmetric.ivSize;
        settings.signature.algorithm = defaultSettings.signature.algorithm;
        return settings;
    }

    static public CryptoSettings empty() {
        return new CryptoSettings();
    }

    static public CryptoSettings.SignatureSettings signatureSettings(String algorithm) {
        CryptoSettings cryptoSettings = getDefault();
        cryptoSettings.signature.algorithm = algorithm;
        return cryptoSettings.signature;
    }

    static public CryptoSettings.SignatureSettings signatureSettings() {
        CryptoSettings settings = empty();
        CryptoSettings defaultSettings = getDefault();

        settings.signature.algorithm = defaultSettings.signature.algorithm;
        return settings.signature;
    }

    static public CryptoSettings.SymmetricSettings symmetricSettings(String keyType, String algorithm) {
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
