/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;


import org.fejoa.library.remote2.CreateAccountJob;
import org.fejoa.library.remote2.JsonRPC;
import org.fejoa.library.remote2.JsonRPCHandler;
import org.fejoa.library.remote2.RootLoginJob;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.Scanner;


public class RootLoginParameterRequestHandler extends JsonRequestHandler {
    public RootLoginParameterRequestHandler() {
        super(RootLoginJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String userName = params.getString(CreateAccountJob.USER_NAME_KEY);

        String content = new Scanner(new File(userName, CreateAccountHandler.ACCOUNT_INFO_FILE)).useDelimiter("\\Z")
                .next();
        JSONObject userConfig = new JSONObject(content);

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
    }
}
