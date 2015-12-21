/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.*;
import org.fejoa.library2.*;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;


public class ContactRequest implements ICommand {
    static final public String COMMAND_NAME = "contactRequest";
    static final String SIGNING_KEY_KEY = "signingKey";
    static final String SIGNING_KEY_SETTINGS_KEY = "signingKeySettings";
    static final String PUBLIC_KEY_KEY = "publicKey";
    static final String PUBLIC_KEY_SETTINGS_KEY = "publicKeySettings";
    static final String SIGNATURE_KEY = "signature";
    static final String SIGNATURE_SETTINGS_KEY = "signatureSettings";
    static final String USER_KEY = "user";
    static final String USER_SERVER_KEY = "server";

    final private FejoaContext context;
    final private ContactPrivate myself;
    final private Remote myServer;

    public ContactRequest(FejoaContext context, ContactPrivate myself, Remote myServer) {
        this.context = context;
        this.myself = myself;
        this.myServer = myServer;
    }

    @Override
    public byte[] getCommand() {
        KeyId signKeyId= myself.getSignatureKeys().getDefault().getKeyId();
        byte[] signKey = myself.getSignatureKey(signKeyId).getKeyPair().getPublic().getEncoded();
        String base64SignKey = DatatypeConverter.printBase64Binary(signKey);
        KeyPairItem publicKeyPair = myself.getEncryptionKeys().getDefault();
        byte[] publicKey = publicKeyPair.getKeyPair().getPublic().getEncoded();
        String base64PublicKey = DatatypeConverter.printBase64Binary(publicKey);

        JSONObject object = new JSONObject();
        try {
            object.put(Constants.COMMAND_NAME_KEY, COMMAND_NAME);
            object.put(USER_KEY, myServer.getUser());
            object.put(USER_SERVER_KEY, myServer.getServer());
            object.put(Constants.ID_KEY, myself.getId());
            object.put(SIGNING_KEY_KEY, base64SignKey);
            object.put(SIGNING_KEY_SETTINGS_KEY, JsonCryptoSettings.toJson(
                    myself.getSignatureKey(signKeyId).getKeyTypeSettings()));
            object.put(PUBLIC_KEY_KEY, base64PublicKey);
            object.put(PUBLIC_KEY_SETTINGS_KEY, JsonCryptoSettings.toJson(publicKeyPair.getKeyTypeSettings()));

            String hash = CryptoHelper.sha256HashHex(myself.getId() + base64SignKey + base64PublicKey);
            CryptoSettings.Signature signatureSettings = context.getCryptoSettings().signature;
            String signature = DatatypeConverter.printBase64Binary(myself.sign(signKeyId, hash.getBytes(),
                    signatureSettings));

            object.put(SIGNATURE_KEY, signature);
            object.put(SIGNATURE_SETTINGS_KEY, JsonCryptoSettings.toJson(signatureSettings));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return object.toString().getBytes();
    }
}
