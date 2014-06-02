/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


public class CryptoHelper {
    public static String toHex(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < bytes.length; i++)
            stringBuffer.append(Integer.toHexString(0xFF & bytes[i]));

        return stringBuffer.toString();
    }

    static public byte[] sha1Hash(byte data[]) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            messageDigest.reset();
            messageDigest.update(data);
            return messageDigest.digest();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    static public byte[] sha256Hash(byte data[]) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.reset();
            messageDigest.update(data);
            return messageDigest.digest();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    static public SecretKey symmetricKeyFromRaw(byte[] key) {
        return new SecretKeySpec(key, 0, key.length, CryptoSettings.SYMMETRIC_KEY_TYPE);
    }

    static private String convertToPEM(String type, Key key)
    {
        String pemKey = new String();
        pemKey += "-----BEGIN " + type + "-----\n";
        pemKey += DatatypeConverter.printBase64Binary(key.getEncoded());
        pemKey += "-----END " + type + "-----";
        return pemKey;
    }

    static public String convertToPEM(PublicKey key)
    {
        return convertToPEM("PUBLIC KEY", key);
    }

    static public String convertToPEM(PrivateKey key)
    {
        return convertToPEM("PRIVATE KEY", key);
    }

    static public PublicKey publicKeyFromPem(String pemKey) {
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

    static public PrivateKey privateKeyFromPem(String pemKey) {
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
