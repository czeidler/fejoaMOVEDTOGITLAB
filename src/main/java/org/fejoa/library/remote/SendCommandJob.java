/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.apache.commons.io.IOUtils;
import org.fejoa.library.Constants;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class SendCommandJob extends SimpleJsonRemoteJob {
    static final public String METHOD = "command";

    final private byte[] command;
    final private String serverUser;

    public SendCommandJob(byte[] commandData, String serverUser) {
        super(true);

        this.command = commandData;
        this.serverUser = serverUser;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        return jsonRPC.call(METHOD, new JsonRPC.Argument(Constants.SERVER_USER_KEY, serverUser));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        return getResult(returnValue);
    }

    @Override
    public void writeData(OutputStream outputStream) throws IOException {
        IOUtils.write(command, outputStream);
    }
}
