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


public class JsonCreateAccountHandler extends JsonRequestHandler {
    static final public String CREATE_ACCOUNT_METHOD = "createAccount";

    public JsonCreateAccountHandler() {
        super(CREATE_ACCOUNT_METHOD);
    }

    @Override
    public String handle(JsonRPCHandler jsonRPCHandler, JSONArray params, InputStream data, OutputStream response) {
        return null;
    }
}
