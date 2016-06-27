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
import org.fejoa.server.Portal;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;


public class AccessRequestJob extends SimpleJsonRemoteJob {
    static final public String METHOD = "accessRequest";
    static final public String REQUEST_KEY = "request";
    static final public String ACCESS_TOKEN_ID_KEY = "accessTokenId";
    static final public String AUTH_TOKEN_KEY = "authToken";
    static final public String AUTH_TOKEN_SIGNATURE_KEY = "authTokenSignature";
    static final public String ACCESS_ENTRY_KEY = "accessEntry";
    static final public String ACCESS_ENTRY_SIGNATURE_KEY = "accessEntrySignature";

    static final public String PARAMETER_REQUEST = "getAuthToken";
    static final public String AUTH_REQUEST = "auth";

    static public class AuthJob extends SimpleJsonRemoteJob {
        final private String serverName;
        final private AccessTokenContact accessTokenContact;
        final private String authToken;

        public AuthJob(String serverName, AccessTokenContact accessTokenContact, String authToken) {
            super(false);

            this.serverName = serverName;
            this.accessTokenContact = accessTokenContact;
            this.authToken = authToken;
        }

        @Override
        public String getJsonHeader(JsonRPC jsonRPC) throws Exception {
            String authSignature = DatatypeConverter.printBase64Binary(accessTokenContact.signAuthToken(authToken));
            String entrySignature = DatatypeConverter.printBase64Binary(accessTokenContact.getAccessEntrySignature());

            return jsonRPC.call(METHOD, new JsonRPC.Argument(REQUEST_KEY, AUTH_REQUEST),
                    new JsonRPC.Argument(ACCESS_TOKEN_ID_KEY, accessTokenContact.getId()),
                    new JsonRPC.Argument(Constants.SERVER_USER_KEY, serverName),
                    new JsonRPC.Argument(AUTH_TOKEN_SIGNATURE_KEY, authSignature),
                    new JsonRPC.Argument(ACCESS_ENTRY_KEY, accessTokenContact.getAccessEntry()),
                    new JsonRPC.Argument(ACCESS_ENTRY_SIGNATURE_KEY, entrySignature));
        }

        @Override
        protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
            return getResult(returnValue);
        }
    }

    final private String serverUser;
    final private AccessTokenContact accessTokenContact;

    public AccessRequestJob(String serverUser, AccessTokenContact accessTokenContact) {
        super(false);

        this.serverUser = serverUser;
        this.accessTokenContact = accessTokenContact;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        return jsonRPC.call(METHOD, new JsonRPC.Argument(REQUEST_KEY, PARAMETER_REQUEST),
                new JsonRPC.Argument(Constants.SERVER_USER_KEY, serverUser));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        try {
            String authToken = returnValue.getString(AUTH_TOKEN_KEY);
            setFollowUpJob(new AuthJob(serverUser, accessTokenContact, authToken));
            return new Result(Portal.Errors.FOLLOW_UP_JOB, "parameters received");
        } catch (JSONException e) {
            e.printStackTrace();
            return new Result(Portal.Errors.ERROR, "parameter missing");
        }
    }
}
