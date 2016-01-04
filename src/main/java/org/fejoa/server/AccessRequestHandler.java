/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;


import org.fejoa.library2.AccessTokenServer;
import org.fejoa.library2.BranchAccessRight;
import org.fejoa.library2.Constants;
import org.fejoa.library2.remote.AccessRequestJob;
import org.fejoa.library2.remote.JsonRPC;
import org.fejoa.library2.remote.JsonRPCHandler;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.InputStream;


public class AccessRequestHandler extends JsonRequestHandler {
    public AccessRequestHandler() {
        super(AccessRequestJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String request = params.getString("request");

        if (request.equals(AccessRequestJob.PARAMETER_REQUEST)) {
            String response = jsonRPCHandler.makeResult(Portal.Errors.OK, "access request parameter",
                    new JsonRPC.Argument(AccessRequestJob.AUTH_TOKEN_KEY, session.getSessionId()));
            responseHandler.setResponseHeader(response);
        } else if (request.equals(AccessRequestJob.AUTH_REQUEST)) {
            String serverUser = params.getString(Constants.SERVER_USER_KEY);
            String accessTokenId = params.getString(AccessRequestJob.ACCESS_TOKEN_ID_KEY);
            String authToken = session.getSessionId();
            byte[] authTokenSignature = DatatypeConverter.parseBase64Binary(
                    params.getString(AccessRequestJob.AUTH_TOKEN_SIGNATURE_KEY));
            String accessEntry = params.getString(AccessRequestJob.ACCESS_ENTRY_KEY);
            byte[] accessEntrySignature = DatatypeConverter.parseBase64Binary(
                    params.getString(AccessRequestJob.ACCESS_ENTRY_SIGNATURE_KEY));

            AccessTokenServer accessToken = session.getAccessToken(serverUser, accessTokenId);
            if (accessToken == null) {
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.ERROR,
                        "can't find access token"));
                return;
            }

            if (!accessToken.auth(authToken, authTokenSignature)) {
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.ERROR, "auth failed"));
                return;
            }
            if (!accessToken.verify(accessEntry, accessEntrySignature)) {
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.ERROR,
                        "can't verify access entry"));
                return;
            }
            BranchAccessRight branchAccessRight = new BranchAccessRight(new JSONObject(accessEntry));
            for (BranchAccessRight.Entry entry : branchAccessRight.getEntries())
                session.addRole(serverUser, entry.getBranch(), entry.getRights());
            if (branchAccessRight.getType() == BranchAccessRight.MIGRATION_ACCESS)
                session.addMigrationRole(serverUser);

            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.OK, "access request successful"));
        } else
            throw new Exception("Invalid access request: " + request);
    }
}
