/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.*;
import org.fejoa.library.messages.Envelope;
import org.fejoa.library.messages.PublicCryptoEnvelope;
import org.fejoa.library.messages.SignatureEnvelope;
import org.fejoa.library.messages.ZipEnvelope;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
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
        final ContactPrivate myself = userData.getIdentityStore().getMyself();
        IContactFinder<IContactPublic> finder = new IContactFinder<IContactPublic>() {
            @Override
            public IContactPublic get(String contactId) {
                if (contactId.equals(myself.getId()))
                    return myself;
                return null;
            }
        };

        CryptoSettings cryptoSettings = context.getCryptoSettings();

        final String message = "Hallo Fejoa";

        // signature
        KeyPairItem keyItemPair = myself.getSignatureKeys().getDefault();
        byte[] signedData = SignatureEnvelope.sign(message.getBytes(), true, myself, keyItemPair.getKeyId(),
                cryptoSettings.signature);
        byte[] verifiedData = Envelope.unpack(signedData, myself, finder, context);

        assertEquals(message, new String(verifiedData));

        Envelope envelope = new Envelope();

        // zip
        byte[] zippedData = ZipEnvelope.zip(message.getBytes(), true);
        byte[] unzippedData = IOUtils.toByteArray(envelope.unpack(new ByteArrayInputStream(zippedData), myself,
                finder, context));

        assertEquals(message, new String(unzippedData));

        // public, sym encryption
        KeyPairItem key = myself.getEncryptionKeys().getDefault();
        byte[] encryptedData = PublicCryptoEnvelope.encrypt(message.getBytes(), true, key.getKeyId(),
                key.getKeyPair().getPublic(), context);
        byte[] decryptedData = IOUtils.toByteArray(envelope.unpack(new ByteArrayInputStream(encryptedData), myself,
                finder, context));

        assertEquals(message, new String(decryptedData));

        // combination
        InputStream signStream = SignatureEnvelope.signStream(message.getBytes(), true, myself, keyItemPair.getKeyId(),
                cryptoSettings.signature);
        InputStream zipStream = ZipEnvelope.zip(signStream, false);
        InputStream encryptStream = PublicCryptoEnvelope.encrypt(zipStream, false, key.getKeyId(),
                key.getKeyPair().getPublic(), context);
        byte[] rawData = IOUtils.toByteArray(envelope.unpack(encryptStream, myself, finder, context));

        assertEquals(myself.getId(), envelope.getSenderId());
        assertEquals(message, new String(rawData));
    }
}
