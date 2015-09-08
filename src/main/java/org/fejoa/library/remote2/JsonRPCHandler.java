/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


public class JsonRPCHandler {
    private int id;
    private String method;
    private JSONObject jsonObject;

    public JsonRPCHandler(String jsonString) throws JSONException, IOException {
        jsonObject = new JSONObject(jsonString);
        if (!jsonObject.getString("jsonrpc").equals("2.0"))
            throw new IOException("json rpc 2.0 expected");
        id = jsonObject.getInt("id");
        method = jsonObject.getString("method");
    }

    public int getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public JSONArray getParams() throws JSONException {
        return jsonObject.getJSONArray("params");
    }

    static public String makeResult(int id, JsonRPC.ArgumentSet resultSet) {
        JsonRPC.Argument result = new JsonRPC.Argument("result", resultSet);

        String value = "{\"jsonrpc\":\"2.0\"";
        value += ",\"id\":" + id + "," + result + "\"}";
        return value;
    }

    public String makeResult(JsonRPC.ArgumentSet resultSet) {
        return makeResult(getId(), resultSet);
    }

    static public String makeError(int id, int error, String message) {
        return makeResult(id, new JsonRPC.ArgumentSet(new JsonRPC.Argument("status", error),
                new JsonRPC.Argument("message", message)));
    }

    public String makeError(int error, String message) {
        return makeResult(getId(), new JsonRPC.ArgumentSet(new JsonRPC.Argument("status", error),
                new JsonRPC.Argument("message", message)));
    }
}
