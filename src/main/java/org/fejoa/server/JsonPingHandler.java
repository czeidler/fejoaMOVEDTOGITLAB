/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.remote2.JsonPingJob;
import org.fejoa.library.remote2.JsonRPC;
import org.fejoa.library.remote2.JsonRPCHandler;
import org.json.JSONException;

import java.io.*;


public class JsonPingHandler extends JsonRequestHandler {
    public JsonPingHandler() {
        super(JsonPingJob.METHOD);
    }

    @Override
    public String handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data) {
        if (data == null)
            return jsonRPCHandler.makeResult(Portal.Errors.ERROR, "data expected!");

        String text;
        try {
            text = jsonRPCHandler.getParams().getString("text");
        } catch (JSONException e) {
            return jsonRPCHandler.makeResult(Portal.Errors.ERROR, "missing argument");
        }
        String response = jsonRPCHandler.makeResult(Portal.Errors.OK, text + " pong");
        responseHandler.setResponseHeader(response);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(data));
        try {
            String dataLine = bufferedReader.readLine();
            OutputStream outputStream = responseHandler.addData();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            writer.write(dataLine + " PONG");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            return jsonRPCHandler.makeResult(Portal.Errors.ERROR, "IO error: " + e.getMessage());
        }

        return null;
    }
}
