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


public class JsonRPC {
    static private int globalJsonId = 0;
    private int jsonId = 0;

    static public class Argument {
        /*public Argument(String name, Object value) {
            this.name = name;
            this.value = new Gson().toJson(value);
        }*/

        public Argument(String name, int value) {
            this.name = name;
            this.value = Integer.toString(value);
        }

        public Argument(String name, float value) {
            this.name = name;
            this.value = Float.toString(value);
        }

        public Argument(String name, double value) {
            this.name = name;
            this.value = Double.toString(value);
        }

        public Argument(String name, String value) {
            this.name = name;
            this.value = "\"" + value + "\"";
        }

        String name;
        String value;
    }

    public JsonRPC() {
        setNewJsonId();
    }

    public String call(String method, Argument ... argumentList) {
        return call(getJsonId(), method, argumentList);
    }

    public JSONObject getReturnValue(String jsonString) throws IOException, JSONException {
        JSONObject jsonObject = new JSONObject(jsonString);
        if (!jsonObject.getString("jsonrpc").equals("2.0"))
            throw new IOException("json rpc 2.0 expected");
        if (jsonObject.getInt("id") != jsonId)
            throw new IOException("wrong method call id; got: " + jsonObject.getInt("id") + " expected: " + jsonId);
        return jsonObject.getJSONObject("result");
    }

    protected int setNewJsonId() {
        globalJsonId++;
        jsonId = globalJsonId;
        return jsonId;
    }

    protected int getJsonId() {
        return jsonId;
    }

    protected String call(int jsonId, String method, Argument ... argumentList) {
        String request = "{ \"jsonrpc\": \"2.0\", \"method\":\"" + method + "\"";
        if (argumentList.length > 0) {
            request += ",\"params\": {";
            for (int i = 0; i < argumentList.length; i++) {
                Argument argument = argumentList[i];
                request += "\"" + argument.name + "\":" + argument.value + "";
                if (i < argumentList.length - 1)
                    request += ",";
            }
            request += "}";
        }
        request += ", \"id\":" + jsonId + "}";
        return request;
    }
}

