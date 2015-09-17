/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;


import org.fejoa.library.remote2.JsonRPCHandler;
import org.json.JSONArray;

import java.io.InputStream;
import java.io.OutputStream;


abstract public class JsonRequestHandler {
    static public class Errors {
        final static public int INVALID_JSON_REQUEST = -1;
        final static public int NO_HANDLER_FOR_REQUEST = -2;
    }

    private String method;

    public JsonRequestHandler(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    /**
     *
     * @param jsonRPCHandler
     * @param params
     * @param data
     * @param response
     * @return the response header or null
     */
    abstract public String handle(JsonRPCHandler jsonRPCHandler, JSONArray params, InputStream data,
                                  OutputStream response);
}
