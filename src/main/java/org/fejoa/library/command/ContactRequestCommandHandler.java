/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.*;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.security.PublicKey;


public class ContactRequestCommandHandler extends EnvelopeCommandHandler {
    final private ContactStore contactStore;

    static public class ReturnValue extends IncomingCommandManager.ReturnValue {
        final public String contactId;
        final public String state;

        public ReturnValue(int status, String contactId, String state) {
            super(status, ContactRequestCommand.COMMAND_NAME);
            this.contactId = contactId;
            this.state = state;
        }
    }

    public ContactRequestCommandHandler(UserData userData) {
        super(userData, ContactRequestCommand.COMMAND_NAME);
        this.contactStore = userData.getContactStore();
    }

    @Override
    protected IncomingCommandManager.ReturnValue handle(JSONObject command) throws Exception {
        String state = command.getString(ContactRequestCommand.STATE);
        String id = command.getString(Constants.SENDER_ID_KEY);
        if (state.equals(ContactRequestCommand.FINISH_STATE))
            return new ReturnValue(IncomingCommandManager.ReturnValue.HANDLED, id, state);

        String signingKeyBase64 = command.getString(ContactRequestCommand.SIGNING_KEY_KEY);
        CryptoSettings.KeyTypeSettings signingKeySettings = JsonCryptoSettings.keyTypeFromJson(command.getJSONObject(
                ContactRequestCommand.SIGNATURE_SETTINGS_KEY));
        String publicKeyBase64 = command.getString(ContactRequestCommand.PUBLIC_KEY_KEY);
        CryptoSettings.KeyTypeSettings publicKeySettings = JsonCryptoSettings.keyTypeFromJson(command.getJSONObject(
                ContactRequestCommand.PUBLIC_KEY_SETTINGS_KEY));

        byte[] signingKeyRaw = DatatypeConverter.parseBase64Binary(signingKeyBase64);
        PublicKey signingKey = CryptoHelper.publicKeyFromRaw(signingKeyRaw, signingKeySettings.keyType);
        byte[] publicKeyRaw = DatatypeConverter.parseBase64Binary(publicKeyBase64);
        PublicKey publicKey = CryptoHelper.publicKeyFromRaw(publicKeyRaw, publicKeySettings.keyType);

        String serverUser = command.getString(Constants.USER_KEY);
        String server = command.getString(Constants.SERVER_KEY);
        Remote remote = new Remote(serverUser, server);

        PublicKeyItem signingKeyItem = new PublicKeyItem(signingKey, signingKeySettings);
        PublicKeyItem publicKeyItem = new PublicKeyItem(publicKey, publicKeySettings);

        String hash = CryptoHelper.sha256HashHex(id + signingKeyBase64 + publicKeyBase64);
        byte[] signature = DatatypeConverter.parseBase64Binary(command.getString(ContactRequestCommand.SIGNATURE_KEY));
        CryptoSettings.Signature signatureSettings = JsonCryptoSettings.signatureFromJson(command.getJSONObject(
                ContactRequestCommand.SIGNATURE_SETTINGS_KEY));
        ICryptoInterface crypto = context.getCrypto();
        if (!crypto.verifySignature(hash.getBytes(), signature, signingKey, signatureSettings))
            throw new Exception("Contact request with invalid signature!");

        ContactPublic contactPublic = contactStore.addContact(id);
        contactPublic.addSignatureKey(signingKeyItem);
        contactPublic.getSignatureKeys().setDefault(signingKeyItem);
        contactPublic.addEncryptionKey(publicKeyItem);
        contactPublic.getEncryptionKeys().setDefault(publicKeyItem);
        contactPublic.getRemotes().add(remote);
        contactPublic.getRemotes().setDefault(remote);
        contactStore.commit();

        return new ReturnValue(IncomingCommandManager.ReturnValue.HANDLED, id, state);
    }
}
