/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.fejoa.library2.Constants;
import org.fejoa.library2.command.IncomingCommandQueue;
import org.fejoa.library2.remote.JsonRPCHandler;
import org.fejoa.library2.remote.SendCommandJob;

import java.io.IOException;
import java.io.InputStream;


public class CommandHandler extends JsonRequestHandler {
    public CommandHandler() {
        super(SendCommandJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        if (data == null)
            throw new IOException("Command data expected");

        String serverUser = jsonRPCHandler.getParams().getString(Constants.SERVER_USER_KEY);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        IOUtils.copy(data, outputStream);

        IncomingCommandQueue queue = session.getIncomingCommandQueue(serverUser);
        queue.addCommand(outputStream.toByteArray());
        queue.commit();

        String response = jsonRPCHandler.makeResult(Portal.Errors.OK, "command delivered");
        responseHandler.setResponseHeader(response);
    }
}
