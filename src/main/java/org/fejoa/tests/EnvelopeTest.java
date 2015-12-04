/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library2.ContactPrivate;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.KeyPairItem;
import org.fejoa.library2.UserData;
import org.fejoa.library2.messages.Envelope;
import org.fejoa.library2.messages.PublicCryptoEnvelope;
import org.fejoa.library2.messages.SignatureEnvelope;
import org.fejoa.library2.messages.ZipEnvelope;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class EnvelopeTest extends TestCase {
    final List<String> cleanUpDirs = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testEnvelopes() throws Exception {
        String dir = "envelopeTest";
        cleanUpDirs.add(dir);

        FejoaContext context = new FejoaContext(dir);
        UserData userData = UserData.create(context, "test");
        ContactPrivate myself = userData.getIdentityStore().getMyself();
        CryptoSettings cryptoSettings = context.getCryptoSettings();

        final String message = "Hallo Fejoa";

        // signature
        KeyPairItem keyItemPair = myself.getSignatureKeys().getDefault();
        byte[] signedData = SignatureEnvelope.sign(message.getBytes(), true, myself, keyItemPair.getKeyId(),
                cryptoSettings.signature);
        byte[] verifiedData = Envelope.unpack(signedData, myself, context);

        assertEquals(message, new String(verifiedData));

        // zip
        byte[] zippedData = ZipEnvelope.zip(message.getBytes(), true);
        byte[] unzippedData = Envelope.unpack(zippedData, myself, context);

        assertEquals(message, new String(unzippedData));

        // public, sym encryption
        KeyPairItem key = myself.getEncryptionKeys().getDefault();
        byte[] encryptedData = PublicCryptoEnvelope.encrypt(message.getBytes(), true, key.getId(),
                key.getKeyPair().getPublic(), context);
        byte[] decryptedData = Envelope.unpack(encryptedData, myself, context);

        assertEquals(message, new String(decryptedData));

        // combination
        encryptedData = PublicCryptoEnvelope.encrypt(signedData, false, key.getId(),
                key.getKeyPair().getPublic(), context);
        byte[] packedData = ZipEnvelope.zip(encryptedData, false);
        byte[] rawData = Envelope.unpack(packedData, myself, context);

        assertEquals(message, new String(rawData));
    }
}
