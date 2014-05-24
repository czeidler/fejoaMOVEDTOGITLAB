package org.fejoa.library.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.security.spec.KeySpec;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;


public class BCCryptoInterface implements ICryptoInterface {

    public BCCryptoInterface() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public SecretKey deriveKey(String secret, byte[] salt, String algorithm, int keyLength, int iterations)
            throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
        KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, keyLength, iterations);
        return factory.generateSecret(spec);
    }

    @Override
    public KeyPair generateKeyPair(int size) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(size);
        return keyGen.generateKeyPair();
    }

    @Override
    public byte[] encryptAsymmetric(byte[] input, PublicKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(CryptoSettings.ASYMMETRIC_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        return cipher.doFinal(input);
    }

    @Override
    public byte[] decryptAsymmetric(byte[] input, PrivateKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(CryptoSettings.ASYMMETRIC_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);

        return cipher.doFinal(input);
    }

    @Override
    public SecretKey generateSymmetricKey(int size) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(CryptoSettings.SYMMETRIC_KEY_TYPE);
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
    public byte[] encryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CryptoSettings.SYMMETRIC_ALGORITHM);
        IvParameterSpec ips = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ips);
        return cipher.doFinal(input);
    }

    @Override
    public byte[] decryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CryptoSettings.SYMMETRIC_ALGORITHM);
        IvParameterSpec ips = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ips);
        return cipher.doFinal(input);
    }

    @Override
    public byte[] sign(byte[] input, PrivateKey key) throws Exception {
        Signature signature = Signature.getInstance(CryptoSettings.SIGNATURE_ALGORITHM);

        signature.initSign(key);
        signature.update(input);
        return signature.sign();
    }

    @Override
    public boolean verifySignature(byte[] message, byte[] signature, PublicKey key) throws Exception {
        Signature sig = Signature.getInstance(CryptoSettings.SIGNATURE_ALGORITHM);

        sig.initVerify(key);
        sig.update(message);
        return sig.verify(signature);
    }
}
