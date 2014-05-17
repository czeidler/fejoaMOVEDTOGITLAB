package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.crypto.BCCryptoInterface;


public class BCCryptoInterfaceTest extends TestCase {

    public void testCryto() throws Exception {
        BCCryptoInterface cryptoInterface = new BCCryptoInterface();
        String keyPair[] = cryptoInterface.generateKeyPair();
        String publicKey = keyPair[0];
        String privateKey = keyPair[1];

        String clearText = "hello crypto";
        byte cipher[] = cryptoInterface.encryptAsymmetric(clearText.getBytes(), publicKey);
        byte result[] = cryptoInterface.decryptAsymmetric(cipher, privateKey);

        assertEquals(clearText, new String(result));
    }

}
