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
import org.fejoa.library.remote2.RemoteMessage;
import org.json.JSONArray;

import java.io.InputStream;

public class JsonPingHandler extends JsonRequestHandler {
    public JsonPingHandler() {
        super(JsonPingJob.PING_METHOD);
    }

    @Override
    RemoteMessage handle(JsonRPCHandler jsonRPCHandler, JSONArray params, InputStream data) {
        return new RemoteMessage(jsonRPCHandler.makeResult(new JsonRPC.ArgumentSet(new JsonRPC.Argument("status", 0),
                new JsonRPC.Argument("message", "pong"))), null);
    }
}
