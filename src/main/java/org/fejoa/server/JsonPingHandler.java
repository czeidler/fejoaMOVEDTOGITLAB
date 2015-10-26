/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library2.remote.JsonPingJob;
import org.fejoa.library2.remote.JsonRPCHandler;
import org.json.JSONException;

import java.io.*;


public class JsonPingHandler extends JsonRequestHandler {
    public JsonPingHandler() {
        super(JsonPingJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        if (data == null)
            throw new IOException("data expected!");

        String text;
        try {
            text = jsonRPCHandler.getParams().getString("text");
        } catch (JSONException e) {
            throw new IOException("missing argument");
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
            throw new IOException("IO error: " + e.getMessage());
        }
    }
}
