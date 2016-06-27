/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.*;
import org.json.JSONObject;


public class AccessCommandHandler extends EnvelopeCommandHandler {
    static public class ReturnValue extends IncomingCommandManager.ReturnValue {
        final public String contactId;
        final public AccessTokenContact accessTokenContact;

        public ReturnValue(int status, String command, String contactId, AccessTokenContact accessTokenContact) {
            super(status, command);
            this.contactId = contactId;
            this.accessTokenContact = accessTokenContact;
        }
    }

    final private ContactStore contactStore;

    public AccessCommandHandler(UserData userData) {
        super(userData, AccessCommand.COMMAND_NAME);
        this.contactStore = userData.getContactStore();
    }

    @Override
    protected IncomingCommandManager.ReturnValue handle(JSONObject command) throws Exception {
        if (!command.getString(Constants.COMMAND_NAME_KEY).equals(AccessCommand.COMMAND_NAME))
            return null;
        String senderId = command.getString(Constants.SENDER_ID_KEY);
        String accessToken = command.getString(AccessCommand.TOKEN_KEY);

        AccessTokenContact accessTokenContact = new AccessTokenContact(context, accessToken);
        ContactPublic sender = contactStore.getContactList().get(senderId);
        sender.getAccessTokenList().add(accessTokenContact);

        contactStore.commit();

        return new ReturnValue(IncomingCommandManager.ReturnValue.HANDLED, AccessCommand.COMMAND_NAME, senderId,
                accessTokenContact);
    }
}
