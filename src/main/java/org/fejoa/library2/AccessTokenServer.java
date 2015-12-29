/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library2.database.StorageDir;
import org.fejoa.library2.util.CryptoSettingsIO;

import java.io.IOException;
import java.security.PublicKey;

/**
 * Public part of the token that is readable by the server.
 */
public class AccessTokenServer {
    final private FejoaContext context;

    final private CryptoSettings.Signature contactAuthKeySettings = new CryptoSettings.Signature();
    final private PublicKey contactAuthKey;
    final private PublicKey accessSignatureKey;
    final private CryptoSettings.Signature accessSignatureKeySettings = new CryptoSettings.Signature();

    public AccessTokenServer(FejoaContext context, StorageDir dir) throws IOException {
        this.context = context;

        CryptoSettingsIO.read(contactAuthKeySettings, dir, AccessToken.CONTACT_AUTH_KEY_SETTINGS_KEY);
        try {
            contactAuthKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(AccessToken.CONTACT_AUTH_PUBLIC_KEY_KEY),
                    contactAuthKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        CryptoSettingsIO.read(accessSignatureKeySettings, dir, AccessToken.SIGNATURE_KEY_SETTINGS_KEY);
        try {
            accessSignatureKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(AccessToken.SIGNATURE_VERIFICATION_KEY_KEY),
                    accessSignatureKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public boolean auth(String authToken, byte[] signature) throws CryptoException {
        return context.getCrypto().verifySignature(authToken.getBytes(), signature, contactAuthKey,
                contactAuthKeySettings);
    }

    public boolean verify(String accessEntry, byte[] signature) throws CryptoException {
        return context.getCrypto().verifySignature(accessEntry.getBytes(), signature, accessSignatureKey,
                accessSignatureKeySettings);
    }
}
