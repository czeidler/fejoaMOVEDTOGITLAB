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
import java.util.ArrayList;
import java.util.List;


public class CryptoHelper {
    public static String toHex(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < bytes.length; i++)
            stringBuffer.append(String.format("%02X", bytes[i]));

        return stringBuffer.toString().toLowerCase();
    }

    static public MessageDigest sha1Hash() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-1");
    }

    static public MessageDigest sha256Hash() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    static public String sha1HashHex(byte data[]) {
        return toHex(sha1Hash(data));
    }

    static public String sha1HashHex(String data) {
        return sha1HashHex(data.getBytes());
    }

    static public String sha256HashHex(byte data[]) {
        return toHex(sha256Hash(data));
    }

    static public String sha256HashHex(String data) {
        return sha256HashHex(data.getBytes());
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

    private static List<String> splitIntoEqualParts(String string, int partitionSize) {
        List<String> parts = new ArrayList<>();
        int length = string.length();
        for (int i = 0; i < length; i += partitionSize)
            parts.add(string.substring(i, Math.min(length, i + partitionSize)));
        return parts;
    }

    static private String convertToPEM(String type, Key key) {
        String pemKey = new String();
        pemKey += "-----BEGIN " + type + "-----\n";
        List<String> parts = splitIntoEqualParts(DatatypeConverter.printBase64Binary(key.getEncoded()), 64);
        for (String part : parts)
            pemKey += part + "\n";
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
