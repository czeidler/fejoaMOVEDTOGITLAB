/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.*;
import org.fejoa.library.crypto.*;
import org.fejoa.library.mailbox.SignatureEnvelopeReader;
import org.fejoa.library.mailbox.SignatureEnvelopeWriter;

import java.io.IOException;
import java.security.KeyPair;


public class EnvelopeTest extends TestCase {
    private ICryptoInterface crypto = Crypto.get();
    private KeyId personalKeyId;
    private ContactPrivate contactPrivate;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        KeyPair personalKey = crypto.generateKeyPair(CryptoSettings.ASYMMETRIC_KEY_SIZE);
        byte hashResult[] = CryptoHelper.sha1Hash(personalKey.getPublic().getEncoded());
        personalKeyId = new KeyId(CryptoHelper.toHex(hashResult));


        contactPrivate = new ContactPrivate(null, null, personalKeyId,
                personalKey);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private IContactFinder getContactFinder() {
        return new IContactFinder() {
            @Override
            public Contact find(String keyId) {
                if (keyId.equals(personalKeyId.getKeyId()))
                    return contactPrivate;
                return null;
            }
        };
    }
    public void testSignatureEnvelope() throws IOException, CryptoException {
        byte[] testData = "test data".getBytes();

        SignatureEnvelopeWriter writer = new SignatureEnvelopeWriter(contactPrivate, personalKeyId, null);
        byte[] packed = writer.pack(testData);
        SignatureEnvelopeReader reader = new SignatureEnvelopeReader(getContactFinder(), null);
        byte[] result = reader.unpack(packed);

        assertEquals(new String(testData), new String(result));
        assertEquals(writer.getUid(), reader.getUid());
    }
}
