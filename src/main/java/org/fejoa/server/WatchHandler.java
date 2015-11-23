/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;


import org.fejoa.library.database.JGitInterface;
import org.fejoa.library2.Constants;
import org.fejoa.library2.remote.JsonRPC;
import org.fejoa.library2.remote.JsonRPCHandler;
import org.fejoa.library2.remote.JsonRemoteJob;
import org.fejoa.library2.remote.WatchJob;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WatchHandler extends JsonRequestHandler {
    public WatchHandler() {
        super(WatchJob.METHOD);
    }

    public enum Status {
        UPDATE,
        ACCESS_DENIED
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String user = params.getString(Constants.SERVER_USER_KEY);
        JSONArray branches = params.getJSONArray(WatchJob.BRANCHES_KEY);

        Map<String, String> branchMap = new HashMap<>();
        for (int i = 0; i < branches.length(); i++) {
            JSONObject branch = branches.getJSONObject(i);
            branchMap.put(branch.getString(WatchJob.BRANCH_KEY), branch.getString(WatchJob.BRANCH_TIP_KEY));
        }

        Map<String, Status> statusMap = watch(session, user, branchMap);

        if (statusMap.isEmpty()) {
            // timeout
            String response = jsonRPCHandler.makeResult(Portal.Errors.OK, "timeout");
            responseHandler.setResponseHeader(response);
            return;
        }

        List<JsonRPC.ArgumentSet> deniedReturn = new ArrayList<>();
        List<JsonRPC.ArgumentSet> statusReturn = new ArrayList<>();
        for (Map.Entry<String, Status> entry : statusMap.entrySet()) {
            if (entry.getValue() == Status.ACCESS_DENIED) {
                JsonRPC.ArgumentSet argumentSet = new JsonRPC.ArgumentSet(
                        new JsonRPC.Argument(WatchJob.BRANCH_KEY, entry.getKey()),
                        new JsonRPC.Argument(WatchJob.STATUS_KEY, WatchJob.STATUS_ACCESS_DENIED)
                );
                deniedReturn.add(argumentSet);
            } else if (entry.getValue() == Status.UPDATE) {
                JsonRPC.ArgumentSet argumentSet = new JsonRPC.ArgumentSet(
                        new JsonRPC.Argument(WatchJob.BRANCH_KEY, entry.getKey()),
                        new JsonRPC.Argument(WatchJob.STATUS_KEY, WatchJob.STATUS_UPDATE)
                );
                statusReturn.add(argumentSet);
            }
        }
        String response = jsonRPCHandler.makeResult(Portal.Errors.OK, "watch results",
                new JsonRPC.Argument(WatchJob.WATCH_RESULT_KEY, statusReturn),
                new JsonRPC.Argument(JsonRemoteJob.ACCESS_DENIED_KEY, statusReturn));
        responseHandler.setResponseHeader(response);
    }

    private Map<String, Status> watch(Session session, String user, Map<String, String> branches) {
        Map<String, Status> status = new HashMap<>();

        //TODO: use a file monitor instead of polling
        final long TIME_OUT = 60 * 1000;
        long time = System.currentTimeMillis();
        while (status.isEmpty()) {
            for (Map.Entry<String, String> entry : branches.entrySet()) {
                String branch = entry.getKey();
                String tip = entry.getValue();
                JGitInterface gitInterface = null;
                try {
                    gitInterface = AccessControl.getReadDatabase(session, user, branch);
                } catch (IOException e) {
                    status.put(branch, Status.ACCESS_DENIED);
                }
                if (gitInterface == null)
                    continue;
                try {
                    if (!tip.equals(gitInterface.getTip()))
                        status.put(branch, Status.UPDATE);
                } catch (IOException e) {
                }
            }
            if (System.currentTimeMillis() - time > TIME_OUT)
                break;
            if (status.isEmpty()) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return status;
    }
}
