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
import org.json.JSONArray;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Contact side of the access token.
 */
public class AccessTokenContact {
    final private FejoaContext context;
    final private String rawAccessToken;

    final private String id;
    final private CryptoSettings.Signature contactAuthKeySettings;
    final private PrivateKey contactAuthKey;
    final private byte[] accessEntrySignature;
    final private String accessEntry;
    final private List<AccessRight> accessRights = new ArrayList<>();

    public AccessTokenContact(FejoaContext context, String rawAccessToken) throws Exception {
        this.context = context;
        this.rawAccessToken = rawAccessToken;

        JSONObject jsonObject = new JSONObject(rawAccessToken);
        id = jsonObject.getString(Constants.ID_KEY);
        accessEntrySignature = DatatypeConverter.parseBase64Binary(jsonObject.getString(
                AccessToken.ACCESS_ENTRY_SIGNATURE_KEY));
        accessEntry = jsonObject.getString(AccessToken.ACCESS_ENTRY_KEY);
        contactAuthKeySettings = JsonCryptoSettings.signatureFromJson(jsonObject.getJSONObject(
                AccessToken.CONTACT_AUTH_KEY_JSON_SETTINGS_KEY));
        byte[] rawKey = DatatypeConverter.parseBase64Binary(
                jsonObject.getString(AccessToken.CONTACT_AUTH_PRIVATE_KEY_KEY));
        contactAuthKey = CryptoHelper.privateKeyFromRaw(rawKey, contactAuthKeySettings.keyType);

        // access rights
        JSONArray accessObject = new JSONArray(accessEntry);
        for (int i = 0; i < accessObject.length(); i++) {
            JSONObject accessRight = accessObject.getJSONObject(i);
            AccessRight right = new AccessRight(accessRight);
            accessRights.add(right);
        }
    }

    public String getId() {
        return id;
    }

    public String getRawAccessToken() {
        return rawAccessToken;
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
