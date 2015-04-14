/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;


public class BCCryptoInterface implements ICryptoInterface {
    public BCCryptoInterface() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public SecretKey deriveKey(String secret, byte[] salt, String algorithm, int iterations, int keyLength)
            throws CryptoException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, keyLength);
            return factory.generateSecret(spec);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public KeyPair generateKeyPair(int size) throws CryptoException {
        KeyPairGenerator keyGen;
        try {
            keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(size);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
        return keyGen.generateKeyPair();
    }

    @Override
    public byte[] encryptAsymmetric(byte[] input, PublicKey key) throws CryptoException {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(CryptoSettings.ASYMMETRIC_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public byte[] decryptAsymmetric(byte[] input, PrivateKey key) throws CryptoException {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(CryptoSettings.ASYMMETRIC_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public SecretKey generateSymmetricKey(int size) throws CryptoException {
        KeyGenerator keyGenerator;
        try {
            keyGenerator = KeyGenerator.getInstance(CryptoSettings.SYMMETRIC_KEY_TYPE);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
        keyGenerator.init(size);
        return keyGenerator.generateKey();
    }

    @Override
    public byte[] generateInitializationVector(int size) {
        SecureRandom random = new SecureRandom();
        return random.generateSeed(size);
    }

    @Override
    public byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        return random.generateSeed(32);
    }

    @Override
    public byte[] encryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv) throws CryptoException {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(CryptoSettings.SYMMETRIC_ALGORITHM);
            IvParameterSpec ips = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ips);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public byte[] decryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv) throws CryptoException {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(CryptoSettings.SYMMETRIC_ALGORITHM);
            IvParameterSpec ips = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ips);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public byte[] sign(byte[] input, PrivateKey key) throws CryptoException {
        Signature signature;
        try {
            signature = Signature.getInstance(CryptoSettings.SIGNATURE_ALGORITHM);

            signature.initSign(key);
            signature.update(input);
            return signature.sign();
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public boolean verifySignature(byte[] message, byte[] signature, PublicKey key) throws CryptoException {
        Signature sig;
        try {
            sig = Signature.getInstance(CryptoSettings.SIGNATURE_ALGORITHM);

            sig.initVerify(key);
            sig.update(message);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }
}
