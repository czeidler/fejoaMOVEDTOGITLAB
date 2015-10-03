/*
 * Copyright 2014-2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.crypto.*;

import javax.crypto.SecretKey;


public class ParcelCrypto {
    ICryptoInterface crypto = Crypto.get();
    final byte iv[];
    final SecretKey key;
    final CryptoSettings.Symmetric symmetricSettings;

    public ParcelCrypto(CryptoSettings.Symmetric settings)
            throws CryptoException {
        this.symmetricSettings = settings;
        iv = crypto.generateInitializationVector(settings.ivSize);
        key = crypto.generateSymmetricKey(settings);
    }

    public ParcelCrypto(byte iv[], byte[] symmetricKey, CryptoSettings.Symmetric settings)
            throws CryptoException {
        this.symmetricSettings = settings;
        this.iv = iv;

        key = CryptoHelper.symmetricKeyFromRaw(symmetricKey, settings);
    }

    public ParcelCrypto(ParcelCrypto parcelCrypto) {
        this.symmetricSettings = parcelCrypto.symmetricSettings;
        this.iv = parcelCrypto.iv;
        this.key = parcelCrypto.key;
    }

    public byte[] cloakData(byte data[]) throws CryptoException {
        return crypto.encryptSymmetric(data, key, iv, symmetricSettings);
    }

    public byte[] uncloakData(byte cloakedData[]) throws CryptoException {
        return crypto.decryptSymmetric(cloakedData, key, iv, symmetricSettings);
    }

    public byte[] getIV() {
        return iv;
    }

    public SecretKey getSymmetricKey() {
        return key;
    }

    public CryptoSettings.Symmetric getSymmetricSettings() {
        return symmetricSettings;
    }
}
