/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.fejoa.library2.Constants;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.Storage;
import org.fejoa.library2.database.StorageDir;
import org.fejoa.server.Portal;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;


public class WatchJob extends SimpleJsonRemoteJob<WatchJob.Result> {
    static final public String METHOD = "watch";
    static final public String BRANCHES_KEY = "branches";
    static final public String BRANCH_KEY = "branch";
    static final public String BRANCH_TIP_KEY = "tip";
    static final public String STATUS_KEY = "tip";
    static final public String STATUS_ACCESS_DENIED = "denied";
    static final public String STATUS_UPDATE = "update";
    static final public String WATCH_RESULT_KEY = "watchResults";
    static final public String PEEK_KEY = "peek";

    public static class Result extends RemoteJob.Result {
        final public List<String> updated;
        public Result(int status, String message, List<String> updated) {
            super(status, message);

            this.updated = updated;
        }
    }

    final private FejoaContext context;
    final private String serverUser;
    final private Collection<Storage> storageList;
    final private boolean peek;

    public WatchJob(FejoaContext context, String serverUser, Collection<Storage> storageList) {
        this(context, serverUser, storageList, false);
    }

    public WatchJob(FejoaContext context, String serverUser, Collection<Storage> storageList, boolean peek) {
        super(false);

        this.context = context;
        this.serverUser = serverUser;
        this.storageList = storageList;
        this.peek = peek;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(Constants.SERVER_USER_KEY, serverUser);

        List<JsonRPC.ArgumentSet> branches = new ArrayList<>();
        for (Storage storage : storageList) {
            StorageDir dir = context.getStorage(storage.getId());
            JsonRPC.ArgumentSet argumentSet = new JsonRPC.ArgumentSet(
                    new JsonRPC.Argument(BRANCH_KEY, dir.getBranch()),
                    new JsonRPC.Argument(BRANCH_TIP_KEY, dir.getTip())
            );
            branches.add(argumentSet);
        }

        if (peek)
            return jsonRPC.call(METHOD, serverUserArg, new JsonRPC.Argument(BRANCHES_KEY, branches));
        return jsonRPC.call(METHOD, serverUserArg, new JsonRPC.Argument(PEEK_KEY, peek), new JsonRPC.Argument(BRANCHES_KEY, branches));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        int status;
        String message;
        try {
            status = returnValue.getInt("status");
            message = returnValue.getString("message");
        } catch (Exception e) {
            status = Portal.Errors.EXCEPTION;
            e.printStackTrace();
            message = e.getMessage();
        }
        if (status != Portal.Errors.DONE)
            return new WatchJob.Result(status, message, null);

        List<String> updates = new ArrayList<>();
        try {
            JSONArray statusArray = returnValue.getJSONArray(WATCH_RESULT_KEY);
            for (int i = 0; i < statusArray.length(); i++) {
                JSONObject statusObject = statusArray.getJSONObject(i);
                String branch = statusObject.getString(BRANCH_KEY);
                updates.add(branch);
            }
        } catch (JSONException e) {
            return new WatchJob.Result(Portal.Errors.EXCEPTION, e.getMessage(), null);
        }

        return new WatchJob.Result(status, message, updates);
    }
}
