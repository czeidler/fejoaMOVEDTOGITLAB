/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library2.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


public class AccessCommand extends EncryptedZipSignedCommand {
    static final public String COMMAND_NAME = "grantAccess";
    static final public String TOKEN_KEY = "token";

    static private String makeCommand(ContactPrivate sender, AccessToken token) throws JSONException, CryptoException {
        JSONObject command = new JSONObject();
        command.put(Constants.COMMAND_NAME_KEY, COMMAND_NAME);
        command.put(Constants.SENDER_ID_KEY, sender.getId());
        command.put(TOKEN_KEY, token.getContactToken());
        return command.toString();
    }

    public AccessCommand(FejoaContext context, ContactPrivate sender, ContactPublic contact, AccessToken token)
            throws CryptoException, JSONException, IOException {
        super(context, makeCommand(sender, token), sender, contact);
    }
}
