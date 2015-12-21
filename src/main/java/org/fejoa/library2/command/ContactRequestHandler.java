/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library2.*;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.security.PublicKey;


public class ContactRequestHandler implements IncomingCommandManager.Handler {
    final private FejoaContext context;
    final private ContactStore contactStore;

    static public class ReturnValue extends IncomingCommandManager.ReturnValue {
        final public String contactId;

        public ReturnValue(int status, String command, String contactId) {
            super(status, command);
            this.contactId = contactId;
        }
    }

    public ContactRequestHandler(FejoaContext context, ContactStore contactStore) {
        this.context = context;
        this.contactStore = contactStore;
    }

    @Override
    public IncomingCommandManager.ReturnValue handle(CommandQueue.Entry command) throws Exception {
        JSONObject object = new JSONObject(new String(command.getData()));

        String id = object.getString(Constants.ID_KEY);
        String signingKeyBase64 = object.getString(ContactRequest.SIGNING_KEY_KEY);
        CryptoSettings.KeyTypeSettings signingKeySettings = JsonCryptoSettings.keyTypeFromJson(object.getJSONObject(
                ContactRequest.SIGNATURE_SETTINGS_KEY));
        String publicKeyBase64 = object.getString(ContactRequest.PUBLIC_KEY_KEY);
        CryptoSettings.KeyTypeSettings publicKeySettings = JsonCryptoSettings.keyTypeFromJson(object.getJSONObject(
                ContactRequest.PUBLIC_KEY_SETTINGS_KEY));

        byte[] signingKeyRaw = DatatypeConverter.parseBase64Binary(signingKeyBase64);
        PublicKey signingKey = CryptoHelper.publicKeyFromRaw(signingKeyRaw, signingKeySettings.keyType);
        byte[] publicKeyRaw = DatatypeConverter.parseBase64Binary(publicKeyBase64);
        PublicKey publicKey = CryptoHelper.publicKeyFromRaw(publicKeyRaw, publicKeySettings.keyType);

        String serverUser = object.getString(ContactRequest.USER_KEY);
        String server = object.getString(ContactRequest.USER_SERVER_KEY);
        Remote remote = new Remote(serverUser, server);

        PublicKeyItem signingKeyItem = new PublicKeyItem(signingKey, signingKeySettings);
        PublicKeyItem publicKeyItem = new PublicKeyItem(publicKey, publicKeySettings);

        String hash = CryptoHelper.sha256HashHex(id + signingKeyBase64 + publicKeyBase64);
        byte[] signature = DatatypeConverter.parseBase64Binary(object.getString(ContactRequest.SIGNATURE_KEY));
        CryptoSettings.Signature signatureSettings = JsonCryptoSettings.signatureFromJson(object.getJSONObject(
                ContactRequest.SIGNATURE_SETTINGS_KEY));
        ICryptoInterface crypto = context.getCrypto();
        if (!crypto.verifySignature(hash.getBytes(), signature, signingKey, signatureSettings))
            throw new Exception("Contact request with invalid signature!");

        ContactPublic contactPublic = contactStore.addContact(id);
        contactPublic.addSignatureKey(signingKeyItem);
        contactPublic.addEncryptionKey(publicKeyItem);
        contactPublic.getRemotes().add(remote);
        contactPublic.getRemotes().setDefault(remote);
        contactStore.commit();

        return new ReturnValue(IncomingCommandManager.ReturnValue.HANDLED,
                ContactRequest.COMMAND_NAME, id);
    }
}
