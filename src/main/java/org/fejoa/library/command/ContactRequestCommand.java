/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.*;
import org.fejoa.library.*;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;


public class ContactRequestCommand {
    static final public String COMMAND_NAME = "contactRequest";
    static final String SIGNING_KEY_KEY = "signingKey";
    static final String SIGNING_KEY_SETTINGS_KEY = "signingKeySettings";
    static final String PUBLIC_KEY_KEY = "publicKey";
    static final String PUBLIC_KEY_SETTINGS_KEY = "publicKeySettings";
    static final String SIGNATURE_KEY = "signature";
    static final String SIGNATURE_SETTINGS_KEY = "signatureSettings";
    static final String STATE = "state";

    static final public String INITIAL_STATE = "init";
    static final public String REPLY_STATE = "reply";
    static final public String FINISH_STATE = "finish";


    static String makeInfoCommand(FejoaContext context, ContactPrivate myself, Remote myServer,
                              boolean reply) throws JSONException, CryptoException {
        KeyId signKeyId= myself.getSignatureKeys().getDefault().getKeyId();
        byte[] signKey = myself.getSignatureKey(signKeyId).getKeyPair().getPublic().getEncoded();
        String base64SignKey = DatatypeConverter.printBase64Binary(signKey);
        KeyPairItem publicKeyPair = myself.getEncryptionKeys().getDefault();
        byte[] publicKey = publicKeyPair.getKeyPair().getPublic().getEncoded();
        String base64PublicKey = DatatypeConverter.printBase64Binary(publicKey);

        JSONObject object = new JSONObject();

        object.put(Constants.COMMAND_NAME_KEY, COMMAND_NAME);
        object.put(Constants.USER_KEY, myServer.getUser());
        object.put(Constants.SERVER_KEY, myServer.getServer());
        object.put(Constants.SENDER_ID_KEY, myself.getId());
        object.put(SIGNING_KEY_KEY, base64SignKey);
        object.put(SIGNING_KEY_SETTINGS_KEY, JsonCryptoSettings.toJson(
                myself.getSignatureKey(signKeyId).getKeyTypeSettings()));
        object.put(PUBLIC_KEY_KEY, base64PublicKey);
        object.put(PUBLIC_KEY_SETTINGS_KEY, JsonCryptoSettings.toJson(publicKeyPair.getKeyTypeSettings()));

        if (!reply)
            object.put(STATE, INITIAL_STATE);
        else
            object.put(STATE, REPLY_STATE);

        String hash = CryptoHelper.sha256HashHex(myself.getId() + base64SignKey + base64PublicKey);
        CryptoSettings.Signature signatureSettings = context.getCryptoSettings().signature;
        String signature = DatatypeConverter.printBase64Binary(myself.sign(signKeyId, hash.getBytes(),
                signatureSettings));

        object.put(SIGNATURE_KEY, signature);
        object.put(SIGNATURE_SETTINGS_KEY, JsonCryptoSettings.toJson(signatureSettings));

        return object.toString();
    }

    static private String makeFinishCommand(ContactPrivate myself) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(Constants.COMMAND_NAME_KEY, COMMAND_NAME);
        object.put(STATE, FINISH_STATE);
        object.put(Constants.SENDER_ID_KEY, myself.getId());
        return object.toString();
    }

    static public ICommand makeInitialRequest(FejoaContext context, ContactPrivate myself, Remote myServer)
            throws IOException, JSONException, CryptoException {
        return new ZipCommand(makeInfoCommand(context, myself, myServer, false));
    }

    static public ICommand makeReplyRequest(FejoaContext context, ContactPrivate myself, Remote myServer,
                                            ContactPublic receiver)
            throws IOException, JSONException, CryptoException {
        return new EncryptedZipCommand(context, makeInfoCommand(context, myself, myServer, true), receiver);
    }

    static public ICommand makeFinish(FejoaContext context, ContactPrivate myself,
                                      ContactPublic receiver) throws JSONException, IOException,
            CryptoException {
        return new EncryptedZipSignedCommand(context, makeFinishCommand(myself), myself, receiver);
    }
}
