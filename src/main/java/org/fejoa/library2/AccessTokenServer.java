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
import org.fejoa.library2.database.StorageDir;
import org.fejoa.library2.util.CryptoSettingsIO;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.PublicKey;

/**
 * Public part of the token that is readable by the server.
 */
public class AccessTokenServer {
    final private FejoaContext context;

    final private PublicKey contactAuthKey;
    final private CryptoSettings.Signature contactAuthKeySettings;
    final private PublicKey accessSignatureKey;
    final private CryptoSettings.Signature accessSignatureKeySettings;

    AccessTokenServer(FejoaContext context, PublicKey contactAuthKey,
                      CryptoSettings.Signature contactAuthKeySettings,
                      PublicKey accessSignatureKey, CryptoSettings.Signature accessSignatureKeySettings) {
        this.context = context;
        this.contactAuthKey = contactAuthKey;
        this.contactAuthKeySettings = contactAuthKeySettings;
        this.accessSignatureKey = accessSignatureKey;
        this.accessSignatureKeySettings = accessSignatureKeySettings;
    }

    public AccessTokenServer(FejoaContext context, StorageDir dir) throws IOException {
        this.context = context;
        contactAuthKeySettings = new CryptoSettings.Signature();
        accessSignatureKeySettings = new CryptoSettings.Signature();

        CryptoSettingsIO.read(contactAuthKeySettings, dir, AccessToken.CONTACT_AUTH_KEY_SETTINGS_KEY);
        try {
            contactAuthKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(AccessToken.CONTACT_AUTH_PUBLIC_KEY_KEY),
                    contactAuthKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        CryptoSettingsIO.read(accessSignatureKeySettings, dir, AccessToken.SIGNATURE_KEY_SETTINGS_KEY);
        try {
            accessSignatureKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(AccessToken.ACCESS_VERIFICATION_KEY_KEY),
                    accessSignatureKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public AccessTokenServer(FejoaContext context, JSONObject jsonObject) throws Exception {
        this.context = context;

        contactAuthKeySettings = JsonCryptoSettings.signatureFromJson(jsonObject.getJSONObject(
                AccessToken.CONTACT_AUTH_KEY_SETTINGS_JSON_KEY));
        byte[] rawKey = DatatypeConverter.parseBase64Binary(
                jsonObject.getString(AccessToken.CONTACT_AUTH_PUBLIC_KEY_KEY));
        contactAuthKey = CryptoHelper.publicKeyFromRaw(rawKey, contactAuthKeySettings.keyType);

        accessSignatureKeySettings = JsonCryptoSettings.signatureFromJson(jsonObject.getJSONObject(
                AccessToken.ACCESS_KEY_SETTINGS_JSON_KEY));
        rawKey = DatatypeConverter.parseBase64Binary(
                jsonObject.getString(AccessToken.ACCESS_VERIFICATION_KEY_KEY));
        accessSignatureKey = CryptoHelper.publicKeyFromRaw(rawKey, contactAuthKeySettings.keyType);
    }

    public String getId() {
        return AccessToken.getId(contactAuthKey);
    }

    public boolean auth(String authToken, byte[] signature) throws CryptoException {
        return context.getCrypto().verifySignature(authToken.getBytes(), signature, contactAuthKey,
                contactAuthKeySettings);
    }

    public boolean verify(String accessEntry, byte[] signature) throws CryptoException {
        return context.getCrypto().verifySignature(accessEntry.getBytes(), signature, accessSignatureKey,
                accessSignatureKeySettings);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(AccessToken.CONTACT_AUTH_PUBLIC_KEY_KEY, DatatypeConverter.printBase64Binary(
                contactAuthKey.getEncoded()));
        jsonObject.put(AccessToken.CONTACT_AUTH_KEY_SETTINGS_JSON_KEY, JsonCryptoSettings.toJson(
                contactAuthKeySettings));
        jsonObject.put(AccessToken.ACCESS_VERIFICATION_KEY_KEY, DatatypeConverter.printBase64Binary(
                accessSignatureKey.getEncoded()));
        jsonObject.put(AccessToken.ACCESS_KEY_SETTINGS_JSON_KEY, JsonCryptoSettings.toJson(
                accessSignatureKeySettings));
        return jsonObject;
    }
}
