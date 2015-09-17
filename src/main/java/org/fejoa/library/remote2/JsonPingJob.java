/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;


public class JsonPingJob extends JsonRemoteJob {
    static final public String PING_METHOD = "ping";

    public JsonPingJob() {
        super(false);
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        int status = RemoteJob.Result.ERROR;
        String message;
        try {
            status = returnValue.getInt("status");
            message = returnValue.getString("message");
        } catch (JSONException e) {
            e.printStackTrace();
            message = e.getMessage();
        }
        return new Result(status, message);
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) {
        return jsonRPC.call(PING_METHOD);
    }

    @Override
    public void writeData(OutputStream outputStream) {

    }
}
