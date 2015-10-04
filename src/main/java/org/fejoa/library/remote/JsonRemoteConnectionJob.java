/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.remote2.JsonRPC;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


abstract public class JsonRemoteConnectionJob extends RemoteConnectionJob {
    final protected JsonRPC jsonRPC = new JsonRPC();

    protected int getStatus(JSONObject result) throws IOException, JSONException {
        if (result == null)
            throw new IOException("bad return value");
        if (!result.has("status"))
            throw new IOException("no status field in return");
        return result.getInt("status");
    }

    protected String getMessage(JSONObject result) throws JSONException {
        if (result.has("message"))
            return result.getString("message");
        return "";
    }
}
