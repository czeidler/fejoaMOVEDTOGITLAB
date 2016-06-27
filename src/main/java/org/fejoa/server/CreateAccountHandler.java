/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.remote.CreateAccountJob;
import org.fejoa.library.remote.JsonRPCHandler;
import org.fejoa.library.support.StorageLib;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;


public class CreateAccountHandler extends JsonRequestHandler {
    public CreateAccountHandler() {
        super(CreateAccountJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String error = createAccount(session, params);
        if (error != null) {
            String response = jsonRPCHandler.makeResult(Portal.Errors.ERROR, error);
            responseHandler.setResponseHeader(response);
            return;
        }
        String response = jsonRPCHandler.makeResult(Portal.Errors.OK, "account created");
        responseHandler.setResponseHeader(response);
    }

    /**
     * Creates the account.
     *
     * @param params method arguments
     * @return error string or null
     */
    private String createAccount(Session session, JSONObject params) throws JSONException {
        if (!params.has(CreateAccountJob.PASSWORD_KEY) || !params.has(CreateAccountJob.SALT_BASE64_KEY)
                || !params.has(CreateAccountJob.KDF_ALGORITHM_KEY) || !params.has(CreateAccountJob.KEY_SIZE_KEY)
                || !params.has(CreateAccountJob.KDF_ITERATIONS_KEY) || !params.has(CreateAccountJob.USER_NAME_KEY))
            return "arguments missing";

        String userName = params.getString(CreateAccountJob.USER_NAME_KEY);
        if (userName.contains(".") || userName.contains("/"))
            return "invalid user name";

        File dir = new File(session.getServerUserDir(userName));
        if (dir.exists())
            return "user already exist";
        if (!dir.mkdirs())
            return "can't create user dir";
        try {
            session.getAccountSettings(userName).update(params, null);
        } catch (IOException e) {
            e.printStackTrace();
            StorageLib.recursiveDeleteFile(dir);
            return "failed to write account info";
        }

        return null;
    }
}
