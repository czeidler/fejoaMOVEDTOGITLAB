/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.apache.commons.io.IOUtils;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library2.*;
import org.fejoa.library2.messages.PublicCryptoEnvelope;
import org.fejoa.library2.messages.SignatureEnvelope;
import org.fejoa.library2.messages.ZipEnvelope;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;


public class EncryptedZipSignedCommand implements ICommand {
    final private byte[] command;

    public EncryptedZipSignedCommand(FejoaContext context, String command, ContactPrivate sender,
                                     ContactPublic receiver) throws IOException, CryptoException, JSONException {
        KeyPairItem signKey = sender.getSignatureKeys().getDefault();
        CryptoSettings cryptoSettings = context.getCryptoSettings();
        InputStream signStream = SignatureEnvelope.signStream(command.getBytes(), true, sender, signKey.getKeyId(),
                cryptoSettings.signature);
        InputStream zipStream = ZipEnvelope.zip(signStream, false);

        PublicKeyItem pubKey = receiver.getEncryptionKeys().getDefault();
        InputStream encryptStream = PublicCryptoEnvelope.encrypt(zipStream, false, pubKey.getKeyId(), pubKey.getKey(),
                context);

        this.command = IOUtils.toByteArray(encryptStream);
    }

    @Override
    public byte[] getCommand() {
        return command;
    }
}
