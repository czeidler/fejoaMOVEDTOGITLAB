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
import org.fejoa.library2.ContactPublic;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.PublicKeyItem;
import org.fejoa.library2.messages.PublicCryptoEnvelope;
import org.fejoa.library2.messages.ZipEnvelope;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;


public class EncryptedZipCommand implements ICommand {
    final private byte[] command;

    public EncryptedZipCommand(FejoaContext context, String command, ContactPublic receiver) throws IOException,
            CryptoException, JSONException {
        InputStream zipStream = ZipEnvelope.zip(new ByteArrayInputStream(command.getBytes()), true);

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
