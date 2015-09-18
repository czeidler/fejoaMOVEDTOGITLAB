/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.remote2.GitPushJob;
import org.fejoa.library.remote2.JsonRPCHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class GitPushHandler extends JsonRequestHandler {
    public GitPushHandler() {
        super(GitPushJob.METHOD);
    }

    @Override
    public String handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data) {
        ServerPipe pipe = new ServerPipe(jsonRPCHandler.makeResult(Portal.Errors.OK, "ok"), responseHandler, data);
        BufferedReader reader = new BufferedReader(new InputStreamReader(pipe.getInputStream()));
        try {
            String line = reader.readLine();
            pipe.getOutputStream().write((line + "-response-").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
