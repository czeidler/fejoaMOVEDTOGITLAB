/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.json.JSONObject;

import java.io.*;


public class JsonPingJob extends SimpleJsonRemoteJob {
    static final public String METHOD = "ping";

    public JsonPingJob() {
        super(true);
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        int status = Result.ERROR;
        String message;
        try {
            status = returnValue.getInt("status");
            message = "Header: " + returnValue.getString("message");
            BufferedReader reader = new BufferedReader(new InputStreamReader(binaryData));
            message += " Data: " + reader.readLine();
        } catch (Exception e) {
            e.printStackTrace();
            message = e.getMessage();
        }
        return new Result(status, message);
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) {
        return jsonRPC.call(METHOD, new JsonRPC.Argument("text", "ping"));
    }

    @Override
    public void writeData(OutputStream outputStream) throws IOException {
        outputStream.write("PING".getBytes());
    }
}
