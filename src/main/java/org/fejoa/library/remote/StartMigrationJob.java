/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.AccessTokenServer;
import org.fejoa.library.Constants;
import org.json.JSONObject;

import java.io.InputStream;


public class StartMigrationJob extends SimpleJsonRemoteJob {
    final static public String METHOD = "startMigration";

    final static public String SERVER_ACCESS_TOKEN_KEY = "serverAccessToken";

    final private String serverUser;
    final private AccessTokenServer accessTokenServer;

    public StartMigrationJob(String serverUser, AccessTokenServer accessTokenServer) {
        super(false);

        this.serverUser = serverUser;
        this.accessTokenServer = accessTokenServer;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws Exception {
        return jsonRPC.call(METHOD, new JsonRPC.Argument(Constants.SERVER_USER_KEY, serverUser),
                new JsonRPC.Argument(SERVER_ACCESS_TOKEN_KEY, accessTokenServer.toJson()));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        return getResult(returnValue);
    }

}
