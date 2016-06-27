/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.AccessTokenContact;
import org.fejoa.library.Constants;
import org.json.JSONObject;

import java.io.InputStream;


/**
 * When migrating to a new server this job tells the new server to pull a branch from the old server.
 */
public class RemotePullJob extends SimpleJsonRemoteJob {
    final static public String METHOD = "remotePull";

    final static public String ACCESS_TOKEN_KEY = "accessToken";
    final static public String SOURCE_USER_KEY = "sourceUser";
    final static public String SOURCE_SERVER_KEY = "sourceServer";

    final private String serverUser;
    final private AccessTokenContact accessTokenContact;
    final private String branch;
    final private String sourceUser;
    final private String sourceServer;

    public RemotePullJob(String serverUser, AccessTokenContact accessTokenContact, String branch,
                         String sourceUser, String sourceServer) {
        super(false);

        this.serverUser = serverUser;
        this.accessTokenContact = accessTokenContact;
        this.branch = branch;
        this.sourceUser = sourceUser;
        this.sourceServer = sourceServer;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws Exception {
        return jsonRPC.call(METHOD, new JsonRPC.Argument(Constants.SERVER_USER_KEY, serverUser),
                new JsonRPC.Argument(ACCESS_TOKEN_KEY, accessTokenContact.toJson().toString()),
                new JsonRPC.Argument(Constants.BRANCH_KEY, branch),
                new JsonRPC.Argument(SOURCE_USER_KEY, sourceUser),
                new JsonRPC.Argument(SOURCE_SERVER_KEY, sourceServer));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        return getResult(returnValue);
    }
}
