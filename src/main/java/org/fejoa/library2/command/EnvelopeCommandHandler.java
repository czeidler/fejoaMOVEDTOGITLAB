/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.apache.commons.io.IOUtils;
import org.fejoa.library2.Constants;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.UserData;
import org.fejoa.library2.messages.Envelope;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.logging.Logger;


abstract class EnvelopeCommandHandler implements IncomingCommandManager.Handler {
    final static private Logger LOG = Logger.getLogger(EnvelopeCommandHandler.class.getName());

    final protected FejoaContext context;
    final protected UserData userData;
    final protected String commandType;

    public EnvelopeCommandHandler(UserData userData, String commandType) {
        this.context = userData.getContext();
        this.userData = userData;
        this.commandType = commandType;
    }

    @Override
    public String handlerName() {
        return commandType;
    }

    @Override
    public IncomingCommandManager.ReturnValue handle(CommandQueue.Entry command) throws Exception {
        byte[] request;
        try {
            request = IOUtils.toByteArray(Envelope.unpack(new ByteArrayInputStream(command.getData()),
                    userData.getIdentityStore().getMyself(),
                    userData.getContactStore().getContactFinder(), context));
        } catch (Exception e) {
            LOG.warning("Can't open envelop or not an enveloped command!");
            LOG.info("Command as string: " + new String(command.getData()));
            return null;
        }

        JSONObject object = new JSONObject(new String(request));
        LOG.info("COMMAND: " + object.toString());

        if (!object.has(Constants.COMMAND_NAME_KEY)
                || !object.getString(Constants.COMMAND_NAME_KEY).equals(this.commandType))
            return null;

        return handle(object);
    }

    abstract protected IncomingCommandManager.ReturnValue handle(JSONObject command) throws Exception;
}
