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
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.security.PrivateKey;

/**
 * Contact side of the access token.
 */
public class AccessTokenContact {
    final private FejoaContext context;

    final private CryptoSettings.Signature contactAuthKeySettings;
    final private PrivateKey contactAuthKey;
    final private byte[] accessEntrySignature;
    final private String accessEntry;

    public AccessTokenContact(FejoaContext context, String accessToken) throws Exception {
        this.context = context;

        /*String accessToken = new String(IOUtils.toByteArray(Envelope.unpack(new ByteArrayInputStream(rawAccessToken),
                userData.getIdentityStore().getMyself(), userData.getContactStore().getContactFinder(),
                userData.getContext())));*/

        JSONObject jsonObject = new JSONObject(accessToken);
        accessEntrySignature = DatatypeConverter.parseBase64Binary(jsonObject.getString(
                AccessToken.ACCESS_ENTRY_SIGNATURE_KEY));
        accessEntry = jsonObject.getString(AccessToken.ACCESS_ENTRY_KEY);
        contactAuthKeySettings = JsonCryptoSettings.signatureFromJson(jsonObject.getJSONObject(
                AccessToken.CONTACT_AUTH_KEY_JSON_SETTINGS_KEY));
        byte[] rawKey = DatatypeConverter.parseBase64Binary(
                jsonObject.getString(AccessToken.CONTACT_AUTH_PRIVATE_KEY_KEY));
        contactAuthKey = CryptoHelper.privateKeyFromRaw(rawKey, contactAuthKeySettings.keyType);
    }

    public String getAccessEntry() {
        return accessEntry;
    }

    public byte[] getAccessEntrySignature() {
        return accessEntrySignature;
    }

    public byte[] signAuthToken(String token) throws CryptoException {
        return context.getCrypto().sign(token.getBytes(), contactAuthKey, contactAuthKeySettings);
    }
}
