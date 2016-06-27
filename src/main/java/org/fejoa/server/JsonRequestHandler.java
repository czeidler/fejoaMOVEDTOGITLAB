/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;


import org.fejoa.library.remote.JsonRPCHandler;

import java.io.InputStream;


abstract public class JsonRequestHandler {
    private String method;

    public JsonRequestHandler(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    abstract public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler,
                                InputStream data, Session session) throws Exception;
}
