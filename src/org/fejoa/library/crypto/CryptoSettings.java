package org.fejoa.library.crypto;

public class CryptoSettings {
    final static public String KDF_ALGORITHM = "PBKDF2WithHmacSHA1";
    final static public String SYMMETRIC_KEY_TYPE = "AES";
    final static public String ASYMMETRIC_ALGORITHM = "RSA/NONE/PKCS1PADDING";
    final static public String SYMMETRIC_ALGORITHM = "AES/CBC/PKCS7Padding";
    final static public String SIGNATURE_ALGORITHM = "SHA1withRSA";

    final static public int MASTER_PASSWORD_ITERATIONS = 20000;
    final static public int MASTER_PASSWORD_LENGTH = 256;
    final static public int MASTER_PASSWORD_IV_LENGTH = 16;
}
