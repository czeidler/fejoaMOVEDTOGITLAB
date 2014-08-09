/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.Contact;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.IContactFinder;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.*;
import org.fejoa.library.support.PositionInputStream;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;


class ParcelCrypto {
    private ICryptoInterface crypto = Crypto.get();
    final private byte iv[];
    final private SecretKey key;

    public ParcelCrypto() throws CryptoException {
        iv = crypto.generateInitializationVector(CryptoSettings.SYMMETRIC_KEY_IV_SIZE);
        key = crypto.generateSymmetricKey(CryptoSettings.SYMMETRIC_KEY_SIZE);
    }

    public ParcelCrypto(ContactPrivate owner, KeyId keyId, byte iv[], byte encryptedSymmetricKey[]) throws CryptoException {
        this.iv = iv;

        PrivateKey privateKey = owner.getKeyPair(keyId.getKeyId()).getPrivate();
        byte decryptedSymmetricKey[] = crypto.decryptAsymmetric(encryptedSymmetricKey, privateKey);
        key = CryptoHelper.symmetricKeyFromRaw(decryptedSymmetricKey);
    }

    public ParcelCrypto(ParcelCrypto parcelCrypto)
    {
        this.iv = parcelCrypto.iv;
        this.key = parcelCrypto.key;
    }

    public byte[] cloakData(byte data[]) throws CryptoException {
        return crypto.encryptSymmetric(data, key, iv);
    }

    public byte[] uncloakData(byte cloakedData[]) throws CryptoException {
        return crypto.decryptSymmetric(cloakedData, key, iv);
    }

    public byte[] getIV() {
        return iv;
    }

    public SecretKey getSymmetricKey() {
        return key;
    }

    public byte[] getEncryptedSymmetricKey(Contact receiver, KeyId keyId) throws CryptoException {
        PublicKey publicKey = receiver.getPublicKey(keyId);
        return crypto.encryptAsymmetric(key.getEncoded(), publicKey);
    }
}


interface IParcelEnvelopeWriter {
    public byte[] pack(byte[] parcel) throws IOException, CryptoException;
}

interface IParcelEnvelopeReader {
    public byte[] unpack(byte[] parcel) throws IOException, CryptoException;
}


