/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library2.remote.CreateAccountJob;
import org.fejoa.library2.remote.JsonRPC;
import org.fejoa.library2.remote.JsonRPCHandler;
import org.fejoa.library2.remote.RootLoginJob;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.Scanner;


public class RootLoginRequestHandler extends JsonRequestHandler {
    public RootLoginRequestHandler() {
        super(RootLoginJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String request = params.getString("request");
        String userName = params.getString(CreateAccountJob.USER_NAME_KEY);

        JSONObject userConfig = session.getAccountSettings(userName).getSettings();

        if (request.equals(RootLoginJob.PARAMETER_REQUEST)) {
            String saltBase64 = userConfig.getString(CreateAccountJob.SALT_BASE64_KEY);
            String algorithm = userConfig.getString(CreateAccountJob.KDF_ALGORITHM_KEY);
            int keySize = userConfig.getInt(CreateAccountJob.KEY_SIZE_KEY);
            int iterations = userConfig.getInt(CreateAccountJob.KDF_ITERATIONS_KEY);

            String response = jsonRPCHandler.makeResult(Portal.Errors.OK, "root login parameter",
                    new JsonRPC.Argument(CreateAccountJob.SALT_BASE64_KEY, saltBase64),
                    new JsonRPC.Argument(CreateAccountJob.KDF_ALGORITHM_KEY, algorithm),
                    new JsonRPC.Argument(CreateAccountJob.KEY_SIZE_KEY, keySize),
                    new JsonRPC.Argument(CreateAccountJob.KDF_ITERATIONS_KEY, iterations));
            responseHandler.setResponseHeader(response);
        } else if (request.equals(RootLoginJob.LOGIN_REQUEST)) {
            String receivedPassword = params.getString(CreateAccountJob.PASSWORD_KEY);
            String password = userConfig.getString(CreateAccountJob.PASSWORD_KEY);
            if (receivedPassword.equals(password)) {
                session.addRootRole(userName);
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.OK, "login successful"));
            } else
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.ERROR, "login failed"));
        } else
            throw new Exception("Invalid root login request: " + request);
    }
}
