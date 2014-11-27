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
import java.util.List;


public class JsonRPC {
    static private int globalJsonId = 0;
    private int jsonId = 0;

    static public class ArgumentSet {
        String value;

        public ArgumentSet(Argument... arguments) {
            if (arguments.length > 0) {
                value = "{";
                for (int i = 0; i < arguments.length; i++) {
                    Argument argument = arguments[i];
                    value += argument.toString();
                    if (i < arguments.length - 1)
                        value += ",";
                }
                value += "}";
            }
        }
    }

    static public class Argument {
        String name;
        String value;

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

        public Argument(String name, ArgumentSet argumentSet) {
            this.name = name;
            this.value = argumentSet.value;
        }

        public Argument(String name, List<ArgumentSet> argumentList) {
            this.name = name;
            this.value = "[";
            final int size = argumentList.size();
            for (int i = 0; i < size; i++) {
                ArgumentSet argumentSet = argumentList.get(i);
                this.value += argumentSet.value;
                if (i < size - 1)
                    value += ",";
            }
            this.value += "]";
        }

        public String toString() {
            return "\"" + name + "\":" + value + "";
        }
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
        String request = "{\"jsonrpc\":\"2.0\"";
        request += ",\"id\":" + jsonId + ",\"method\":\"" + method + "\"";
        if (argumentList.length > 0) {
            request += ",";
            Argument paramsArgument = new Argument("params", new ArgumentSet(argumentList));
            request += paramsArgument.toString();
        }
        request += "}";
        return request;
    }
}

