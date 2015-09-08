/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.remote2.JsonRPC;
import org.fejoa.library.remote2.JsonRPCHandler;
import org.fejoa.library.remote2.RemoteMessage;
import org.json.JSONArray;

import java.io.InputStream;


public class JsonCreateAccountHandler extends JsonRequestHandler {
    static final public String CREATE_ACCOUNT_METHOD = "createAccount";

    public JsonCreateAccountHandler() {
        super(CREATE_ACCOUNT_METHOD);
    }

    @Override
    RemoteMessage handle(JsonRPCHandler jsonRPCHandler, JSONArray params, InputStream data) {
        return null;
        //return jsonRPCHandler.makeResult(new JsonRPC.ArgumentSet());
    }
}
