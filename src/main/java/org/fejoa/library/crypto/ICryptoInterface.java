/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;


public interface ICryptoInterface {
    SecretKey deriveKey(String secret, byte[] salt, String algorithm, int keyLength, int iterations)
            throws CryptoException;

    KeyPair generateKeyPair(CryptoSettings.KeyTypeSettings settings) throws CryptoException;
    SecretKey generateSymmetricKey(CryptoSettings.KeyTypeSettings settings) throws CryptoException;
    byte[] generateInitializationVector(int size);
    byte[] generateSalt();

    byte[] encryptAsymmetric(byte[] input, PublicKey key, CryptoSettings.Asymmetric settings)
            throws CryptoException;
    byte[] decryptAsymmetric(byte[] input, PrivateKey key, CryptoSettings.Asymmetric settings)
            throws CryptoException;

    byte[] encryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv, CryptoSettings.Symmetric settings)
            throws CryptoException;
    byte[] decryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv, CryptoSettings.Symmetric settings)
            throws CryptoException;

    OutputStream encryptSymmetric(OutputStream out, SecretKey secretKey, byte[] iv, CryptoSettings.Symmetric settings)
            throws CryptoException;
    InputStream encryptSymmetric(InputStream in, SecretKey secretKey, byte[] iv, CryptoSettings.Symmetric settings)
            throws CryptoException;
    InputStream decryptSymmetric(InputStream input, SecretKey secretKey, byte[] iv, CryptoSettings.Symmetric settings)
            throws CryptoException;

    byte[] sign(byte[] input, PrivateKey key, CryptoSettings.Signature settings) throws CryptoException;
    boolean verifySignature(byte[] message, byte[] signature, PublicKey key, CryptoSettings.Signature settings)
            throws CryptoException;
}
