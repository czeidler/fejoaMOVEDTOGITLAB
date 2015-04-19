/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

public class CryptoSettings {
    public String kdfAlgorithm;
    public String asymmetricAlgorithm;
    public int asymmetricKeySize = -1;
    public int asymmetricKeySizeChannelSign = -1;

    public String symmetricAlgorithm;
    public String symmetricKeyType;
    public int symmetricKeySize = -1;
    public int symmetricKeyIVSize = -1;

    public String signatureAlgorithm;

    public int masterPasswordIterations = -1;
    public int masterPasswordLength = -1;
    public int masterPasswordIVLength = -1;

    private CryptoSettings() {

    }

    static public CryptoSettings getDefault() {
        CryptoSettings cryptoSettings = new CryptoSettings();

        cryptoSettings.kdfAlgorithm = "PBKDF2WithHmacSHA1";
        cryptoSettings.asymmetricAlgorithm = "RSA/NONE/PKCS1PADDING";
        cryptoSettings.asymmetricKeySize = 2048;
        cryptoSettings.asymmetricKeySizeChannelSign = 512;

        cryptoSettings.symmetricAlgorithm = "AES/CBC/PKCS7Padding";
        cryptoSettings.symmetricKeyType = "AES";
        cryptoSettings.symmetricKeySize = 256;
        cryptoSettings.symmetricKeyIVSize = 16;

        cryptoSettings.signatureAlgorithm = "SHA1withRSA";

        cryptoSettings.masterPasswordIterations = 20000;
        cryptoSettings.masterPasswordLength = 256;
        cryptoSettings.masterPasswordIVLength = 16;

        return cryptoSettings;
    }

    static public CryptoSettings getFast() {
        CryptoSettings cryptoSettings = getDefault();
        cryptoSettings.asymmetricKeySize = 512;

        cryptoSettings.symmetricKeySize = 128;
        cryptoSettings.symmetricKeyIVSize = 16;

        cryptoSettings.masterPasswordIterations = 1;

        return cryptoSettings;
    }

    static public CryptoSettings messageChannel() {
        CryptoSettings settings = empty();
        CryptoSettings defaultSettings = getDefault();

        settings.asymmetricAlgorithm = defaultSettings.asymmetricAlgorithm;
        settings.asymmetricKeySize = defaultSettings.asymmetricKeySize;
        settings.asymmetricKeySizeChannelSign = defaultSettings.asymmetricKeySizeChannelSign;
        settings.symmetricAlgorithm = defaultSettings.symmetricKeyType;
        settings.symmetricKeyType = defaultSettings.symmetricKeyType;
        settings.symmetricKeySize = defaultSettings.symmetricKeySize;
        settings.symmetricKeyIVSize = defaultSettings.symmetricKeyIVSize;
        settings.signatureAlgorithm = defaultSettings.signatureAlgorithm;
        return settings;
    }

    static public CryptoSettings empty() {
        return new CryptoSettings();
    }

    static public CryptoSettings signatureSettings(String algorithm) {
        CryptoSettings cryptoSettings = getDefault();
        cryptoSettings.signatureAlgorithm = algorithm;
        return cryptoSettings;
    }

    static public CryptoSettings signatureSettings() {
        CryptoSettings settings = empty();
        CryptoSettings defaultSettings = getDefault();

        settings.signatureAlgorithm = defaultSettings.signatureAlgorithm;
        return settings;
    }

    static public CryptoSettings symmetricSettings(String keyType, String algorithm) {
        CryptoSettings cryptoSettings = empty();
        cryptoSettings.symmetricKeyType = keyType;
        cryptoSettings.symmetricAlgorithm = algorithm;
        return cryptoSettings;
    }

    static public CryptoSettings symmetricKeyTypeSettings(String keyType) {
        CryptoSettings cryptoSettings = empty();
        cryptoSettings.symmetricKeyType = keyType;
        return cryptoSettings;
    }
}
