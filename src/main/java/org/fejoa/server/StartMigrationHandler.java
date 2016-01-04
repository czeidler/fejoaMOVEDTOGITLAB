/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library2.Constants;
import org.fejoa.library2.remote.JsonRPCHandler;
import org.fejoa.library2.remote.StartMigrationJob;
import org.json.JSONObject;

import java.io.*;


public class StartMigrationHandler extends JsonRequestHandler {
    public static String MIGRATION_INFO_FILE = "migration.info";

    public StartMigrationHandler() {
        super(StartMigrationJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String serverUser = params.getString(Constants.SERVER_USER_KEY);
        JSONObject accessTokenServer = params.getJSONObject(StartMigrationJob.SERVER_ACCESS_TOKEN_KEY);

        AccessControl accessControl = new AccessControl(session, serverUser);
        if (!accessControl.canStartMigration()) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.ACCESS_DENIED,
                    "Only root users can start migration."));
            return;
        }

        File migrationFile = new File(session.getServerUserDir(serverUser), MIGRATION_INFO_FILE);
        if (migrationFile.exists()) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.MIGRATION_ALREADY_STARTED,
                    "Migration already started."));
            return;
        }

        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(migrationFile)));
        writer.write(accessTokenServer.toString());
        writer.flush();
        writer.close();

        responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.OK, "Migration started."));
    }
}
