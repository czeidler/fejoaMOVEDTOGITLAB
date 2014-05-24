package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.crypto.BCCryptoInterface;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;


public class BCCryptoInterfaceTest extends TestCase {

    public void testCryto() throws Exception {
        BCCryptoInterface cryptoInterface = new BCCryptoInterface();
        KeyPair keyPair = cryptoInterface.generateKeyPair(1024);

        // encrypt asymmetric + signature
        String clearTextAsym = "hello crypto asymmetric";
        byte encryptedAsymmetric[] = cryptoInterface.encryptAsymmetric(clearTextAsym.getBytes(), keyPair.getPublic());
        byte signature[] = cryptoInterface.sign(clearTextAsym.getBytes(), keyPair.getPrivate());

        // encrypt symmetric
        String clearTextSym = "hello crypto symmetric";
        byte iv[] = cryptoInterface.generateInitializationVector(16);
        SecretKey secretKey = cryptoInterface.generateSymmetricKey(256);
        byte encryptedSymmetric[] = cryptoInterface.encryptSymmetric(clearTextSym.getBytes(), secretKey, iv);

        // store keys to pem and restore
        String privateKeyString = CryptoHelper.convertToPEM(keyPair.getPrivate());
        String publicKeyString = CryptoHelper.convertToPEM(keyPair.getPublic());
        byte secretKeyBytes[] = secretKey.getEncoded();
        PrivateKey privateKey = CryptoHelper.privateKeyFromPem(privateKeyString);
        PublicKey publicKey = CryptoHelper.publicKeyFromPem(publicKeyString);
        secretKey = CryptoHelper.symmetricKeyFromRaw(secretKeyBytes);

        // test if we can decrypt / verify the signature
        byte decryptedAsymmetric[] = cryptoInterface.decryptAsymmetric(encryptedAsymmetric, privateKey);
        assertTrue(Arrays.equals(clearTextAsym.getBytes(), decryptedAsymmetric));
        assertTrue(cryptoInterface.verifySignature(clearTextAsym.getBytes(), signature, publicKey));
        byte decryptedSymmetric[] = cryptoInterface.decryptSymmetric(encryptedSymmetric, secretKey, iv);
        assertTrue(Arrays.equals(clearTextSym.getBytes(), decryptedSymmetric));

        // check if encryption still works with the public key that we converted to pem and back
        byte encryptedAsymmetricAfterPem[] = cryptoInterface.encryptAsymmetric(clearTextAsym.getBytes(), publicKey);
        byte decryptedAsymmetricAfterPem[] = cryptoInterface.decryptAsymmetric(encryptedAsymmetric, privateKey);
        assertTrue(Arrays.equals(clearTextAsym.getBytes(), decryptedAsymmetricAfterPem));

        // test if kdf gives the same value twice
        String password = "testPassword348#";
        byte salt[] = cryptoInterface.generateSalt();
        SecretKey kdfKey1 = cryptoInterface.deriveKey(password, salt, CryptoSettings.KDF_ALGORITHM, 256, 20000);
        SecretKey kdfKey2 = cryptoInterface.deriveKey(password, salt, CryptoSettings.KDF_ALGORITHM, 256, 20000);
        assertTrue(Arrays.equals(kdfKey1.getEncoded(), kdfKey2.getEncoded()));
    }

}
