/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;


public class JsonRPCHandler {
    private int id;
    private String method;
    private JSONObject jsonObject;
    private JSONObject params;

    public JsonRPCHandler(String jsonString) throws JSONException, IOException {
        jsonObject = new JSONObject(jsonString);
        if (!jsonObject.getString("jsonrpc").equals("2.0"))
            throw new IOException("json rpc 2.0 expected");
        id = jsonObject.getInt("id");
        method = jsonObject.getString("method");

        try {
            params = jsonObject.getJSONObject("params");
        } catch (JSONException e) {
        }
    }

    public int getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public JSONObject getParams() {
        return params;
    }

    static public String makeResult(int id, JsonRPC.Argument... arguments) {
        JsonRPC.Argument result = new JsonRPC.Argument("result", new JsonRPC.ArgumentSet(arguments));

        String value = "{\"jsonrpc\":\"2.0\"";
        value += ",\"id\":" + id + "," + result + "}";
        return value;
    }

    public String makeResult(JsonRPC.Argument... arguments) {
        return makeResult(getId(), arguments);
    }

    static private <T> T[] concat(T[] array1, T[] array2) {
        T[] result = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    public String makeResult(int status, String message, JsonRPC.Argument... arguments) {
        return makeResult(getId(), concat(new JsonRPC.Argument[]{new JsonRPC.Argument("status", status),
            new JsonRPC.Argument("message", message)}, arguments));
    }

    static public String makeResult(int id, int error, String message) {
        return makeResult(id, new JsonRPC.Argument("status", error),
                new JsonRPC.Argument("message", message));
    }

    public String makeResult(int error, String message) {
        return makeResult(getId(), error, message);
    }
}
