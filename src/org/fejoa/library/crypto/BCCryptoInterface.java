package org.fejoa.library.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.*;
import javax.xml.bind.DatatypeConverter;


public class BCCryptoInterface {
    public BCCryptoInterface() {
        Security.addProvider(new BouncyCastleProvider());
    }

    public String[] generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024);
        KeyPair key = keyGen.generateKeyPair();

        String pair[] = new String[2];
        pair[0] = convertToPEM(key.getPublic());
        pair[1] = convertToPEM(key.getPrivate());
        return pair;
    }

    public byte[] encryptAsymmetric(byte[] input, String publicKey) throws Exception {
        PublicKey key = publicKeyFromPem(publicKey);

        Cipher cipher = Cipher.getInstance("RSA/NONE/PKCS1PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        return cipher.doFinal(input);
        /*
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CipherOutputStream outputStream = new CipherOutputStream(byteArrayOutputStream, cipher);
        InputStream inputStream = new ByteInputStream(input, 0, input.length);

        byte out[] = null;
        try {
            copy(inputStream, outputStream);
            out = byteArrayOutputStream.toByteArray();
        } finally {
            inputStream.close();
            byteArrayOutputStream.close();
        }

        return out;*/
    }

    public byte[] decryptAsymmetric(byte[] input, String privateKey) throws Exception {
        PrivateKey key = privateKeyFromPem(privateKey);
        Cipher cipher = Cipher.getInstance("RSA/NONE/PKCS1PADDING");
        cipher.init(Cipher.DECRYPT_MODE, key);

        return cipher.doFinal(input);
        /*InputStream inputStream = new ByteInputStream(input, 0, input.length);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);

        byte out[] = null;
        try {
            copy(cipherInputStream, byteArrayOutputStream);
            out = byteArrayOutputStream.toByteArray();
        } finally {
            cipherInputStream.close();
            byteArrayOutputStream.close();
        }

        return out;*/
    }

    private void copy(InputStream is, OutputStream os) throws IOException {
        int i;
        byte[] b = new byte[1024];
        while((i=is.read(b))!=-1) {
            os.write(b, 0, i);
        }
    }

    private String convertToPEM(String type, Key key)
    {
        String pemKey = new String();
        pemKey += "-----BEGIN " + type + "-----\n";
        pemKey += DatatypeConverter.printBase64Binary(key.getEncoded());
        pemKey += "-----END " + type + "-----";
        return pemKey;
    }

    private String convertToPEM(PublicKey key)
    {
        return convertToPEM("PUBLIC KEY", key);
    }

    private String convertToPEM(PrivateKey key)
    {
        return convertToPEM("PRIVATE KEY", key);
    }

    private PublicKey publicKeyFromPem(String pemKey) {
        pemKey = pemKey.replace("-----BEGIN PUBLIC KEY-----\n", "");
        pemKey = pemKey.replace("-----END PUBLIC KEY-----", "");

        byte [] decoded = DatatypeConverter.parseBase64Binary(pemKey);

        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private PrivateKey privateKeyFromPem(String pemKey) {
        pemKey = pemKey.replace("-----BEGIN PRIVATE KEY-----\n", "");
        pemKey = pemKey.replace("-----END PRIVATE KEY-----", "");

        byte [] decoded = DatatypeConverter.parseBase64Binary(pemKey);

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
