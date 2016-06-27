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


public class MigrationCommandHandler extends EnvelopeCommandHandler {
    static public class ReturnValue extends IncomingCommandManager.ReturnValue {
        final public String contactId;

        public ReturnValue(int status, String command, String contactId) {
            super(status, command);
            this.contactId = contactId;
        }
    }

    public MigrationCommandHandler(UserData userData) {
        super(userData, MigrationCommand.COMMAND_NAME);
    }

    @Override
    protected IncomingCommandManager.ReturnValue handle(JSONObject command) throws Exception {
        String senderId = command.getString(Constants.SENDER_ID_KEY);
        String newUserName = command.getString(MigrationCommand.NEW_USER_KEY);
        String newServer = command.getString(MigrationCommand.NEW_SERVER_KEY);

        // update contact entry
        StorageDirList<ContactPublic> contactList = userData.getContactStore().getContactList();
        ContactPublic contact = contactList.get(senderId);
        Remote oldRemote = contact.getRemotes().getDefault();
        Remote newRemote = new Remote(newUserName, newServer);
        contact.getRemotes().add(newRemote);
        contact.getRemotes().setDefault(newRemote);
        userData.getContactStore().commit();

        // update outgoing commands
        userData.getOutgoingCommandQueue().updateReceiver(oldRemote.getUser(), oldRemote.getServer(),
                newRemote.getUser(), newRemote.getServer());
        userData.getOutgoingCommandQueue().commit();

        return new ReturnValue(IncomingCommandManager.ReturnValue.HANDLED, MigrationCommand.COMMAND_NAME, senderId);
    }
}
