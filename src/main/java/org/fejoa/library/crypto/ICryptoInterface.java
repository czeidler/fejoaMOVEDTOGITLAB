/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import javax.crypto.SecretKey;
import java.security.*;


public interface ICryptoInterface {
    SecretKey deriveKey(String secret, byte[] salt, String algorithm, int keyLength, int iterations) throws CryptoException;

    KeyPair generateKeyPair(int size) throws CryptoException;
    SecretKey generateSymmetricKey(int size, CryptoSettings settings) throws CryptoException;
    byte[] generateInitializationVector(int size);
    byte[] generateSalt();

    byte[] encryptAsymmetric(byte[] input, PublicKey key, CryptoSettings settings) throws CryptoException;
    byte[] decryptAsymmetric(byte[] input, PrivateKey key, CryptoSettings settings) throws CryptoException;

    byte[] encryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv, CryptoSettings settings) throws CryptoException;
    byte[] decryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv, CryptoSettings settings) throws CryptoException;

    byte[] sign(byte[] input, PrivateKey key, CryptoSettings settings) throws CryptoException;
    boolean verifySignature(byte[] message, byte[] signature, PublicKey key, CryptoSettings settings) throws CryptoException;
}
