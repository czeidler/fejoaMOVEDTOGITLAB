/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.remote2.CreateAccountJob;
import org.fejoa.library.remote2.JsonRPCHandler;
import org.fejoa.library.remote2.RootLoginJob;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.Scanner;


public class RootLoginRequestHandler extends JsonRequestHandler {
    public RootLoginRequestHandler() {
        super(RootLoginJob.SendPasswordJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data)
            throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String userName = params.getString(CreateAccountJob.USER_NAME_KEY);
        String receivedPassword = params.getString(CreateAccountJob.PASSWORD_KEY);

        String content = new Scanner(new File(userName, CreateAccountHandler.ACCOUNT_INFO_FILE)).useDelimiter("\\Z")
                .next();
        JSONObject userConfig = new JSONObject(content);

        String password = userConfig.getString(CreateAccountJob.PASSWORD_KEY);
        if (receivedPassword.equals(password))
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.OK, "login successful"));
        else
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.ERROR, "login failed"));
    }
}
