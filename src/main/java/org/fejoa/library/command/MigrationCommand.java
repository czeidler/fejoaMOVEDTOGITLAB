/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.Constants;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.ContactPublic;
import org.fejoa.library.FejoaContext;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


public class MigrationCommand extends EncryptedZipSignedCommand  {
    static final public String COMMAND_NAME = "migration";

    static final public String NEW_USER_KEY = "newUser";
    static final public String NEW_SERVER_KEY = "newServer";

    static private String makeCommand(ContactPrivate sender, String newUserName, String newServer)
            throws JSONException {
        JSONObject command = new JSONObject();
        command.put(Constants.COMMAND_NAME_KEY, COMMAND_NAME);
        command.put(Constants.SENDER_ID_KEY, sender.getId());
        command.put(NEW_USER_KEY, newUserName);
        command.put(NEW_SERVER_KEY, newServer);
        return command.toString();
    }

    public MigrationCommand(FejoaContext context, String newUserName, String newServer, ContactPrivate sender,
                            ContactPublic receiver)
            throws IOException, CryptoException, JSONException {
        super(context, makeCommand(sender, newUserName, newServer), sender, receiver);
    }
}
