/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.security.*;


public interface ICryptoInterface {
    public SecretKey deriveKey(String secret, byte[] salt, String algorithm, int keyLength, int iterations)
            throws Exception;

    public KeyPair generateKeyPair(int size) throws CryptoException;
    public SecretKey generateSymmetricKey(int size) throws CryptoException;
    public byte[] generateInitializationVector(int size);
    public byte[] generateSalt();

    public byte[] encryptAsymmetric(byte[] input, PublicKey key) throws CryptoException;
    public byte[] decryptAsymmetric(byte[] input, PrivateKey key) throws CryptoException;

    public byte[] encryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv) throws CryptoException;
    public byte[] decryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv) throws CryptoException;

    public byte[] sign(byte[] input, PrivateKey key) throws CryptoException;
    public boolean verifySignature(byte[] message, byte[] signature, PublicKey key) throws CryptoException;
}
