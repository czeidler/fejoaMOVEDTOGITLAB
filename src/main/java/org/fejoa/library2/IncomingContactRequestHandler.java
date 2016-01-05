/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library2.command.ContactRequestCommand;
import org.fejoa.library2.command.ContactRequestCommandHandler;
import org.fejoa.library2.command.IncomingCommandManager;

import java.util.logging.Logger;


public class IncomingContactRequestHandler {
    public interface IHandler {
        /**
         *  Called after the initial request is received.
         * @param contactPublic
         * @return true to continue
         */
        boolean onInitialRequest(ContactPublic contactPublic);
    }

    static public class AutoAcceptHandler implements IHandler {
        @Override
        public boolean onInitialRequest(ContactPublic contactPublic) {
            return true;
        }
    }

    final static private Logger LOG = Logger.getLogger(IncomingContactRequestHandler.class.getName());

    final private Client client;
    final IncomingCommandManager.IListener listener = new IncomingCommandManager.IListener() {
        @Override
        public void onCommandReceived(IncomingCommandManager.ReturnValue returnValue) {
            if (returnValue.command.equals(ContactRequestCommand.COMMAND_NAME)) {
                String state = ((ContactRequestCommandHandler.ReturnValue)returnValue).state;

                if (state.equals(ContactRequestCommand.INITIAL_STATE)) {
                    onInitialRequest((ContactRequestCommandHandler.ReturnValue)returnValue);
                } else if (state.equals(ContactRequestCommand.REPLY_STATE)) {
                    return;
                } else if (state.equals(ContactRequestCommand.FINISH_STATE)) {
                    LOG.info("mutual contact established");
                } else
                    onException(new Exception("unexpected state"));
            }
        }

        @Override
        public void onException(Exception exception) {
            LOG.severe(exception.getMessage());
        }
    };
    private IHandler handler;

    public IncomingContactRequestHandler(Client client, IHandler handler) {
        this.client = client;
        setHandler(handler);
    }

    public void setHandler(IHandler handler) {
        if (handler == null)
            this.handler = new AutoAcceptHandler();
        else
            this.handler = handler;
    }

    public void start() {
        client.getIncomingCommandManager().addListener(listener);
    }

    public void stop() {
        client.getIncomingCommandManager().removeListener(listener);
    }

    private void onInitialRequest(ContactRequestCommandHandler.ReturnValue returnValue) {
        String contactId = returnValue.contactId;
        ContactPublic contactPublic = client.getUserData().getContactStore().getContactList().get(
                contactId);
        if (contactPublic == null) {
            LOG.severe("contact not found!");
            return;
        }
        if (!handler.onInitialRequest(contactPublic))
            return;

        Remote remote = contactPublic.getRemotes().getDefault();
        try {
            client.getUserData().getOutgoingCommandQueue().post(ContactRequestCommand.makeReplyRequest(
                    client.getContext(),
                    client.getUserData().getIdentityStore().getMyself(),
                    client.getUserData().getRemoteList().getDefault(), contactPublic),
                    remote.getUser(), remote.getServer());
        } catch (Exception e) {
            LOG.severe(e.getMessage());
        }
    }
}
