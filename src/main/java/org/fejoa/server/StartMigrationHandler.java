/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.Constants;
import org.fejoa.library.remote.JsonRPCHandler;
import org.fejoa.library.remote.StartMigrationJob;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.Scanner;


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

        if (!writeMigrationFile(session, serverUser, accessTokenServer)) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.MIGRATION_ALREADY_STARTED,
                    "Migration already started."));
            return;
        }

        responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.OK, "Migration started."));
    }

    /**
     *
     * @param session
     * @param serverUser
     * @param accessTokenServer
     * @return false if file exists
     * @throws IOException
     */
    static public boolean writeMigrationFile(Session session, String serverUser, JSONObject accessTokenServer)
            throws IOException {
        File migrationFile = new File(session.getServerUserDir(serverUser), MIGRATION_INFO_FILE);
        if (migrationFile.exists())
            return false;

        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(migrationFile)));
        writer.write(accessTokenServer.toString());
        writer.flush();
        writer.close();
        return true;
    }

    static public JSONObject readMigrationFile(Session session, String serverUser) throws FileNotFoundException,
            JSONException {
        File migrationFile = new File(session.getServerUserDir(serverUser), MIGRATION_INFO_FILE);
        String content = new Scanner(migrationFile).useDelimiter("\\Z").next();
        return new JSONObject(content);
    }

    static public JSONObject readMigrationAccessToken(Session session, String serverUser) throws FileNotFoundException,
            JSONException {
        return readMigrationFile(session, serverUser);
    }
}
