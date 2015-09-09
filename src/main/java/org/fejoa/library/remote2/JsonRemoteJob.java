/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;


abstract public class JsonRemoteJob extends RemoteJob {
    /**
     * Can be used for errors unrelated to the current job. For example, token expired or auth became invalid.
     */
    public interface IErrorCallback {
         void onError(JSONObject returnValue, InputStream binaryData);
    }

    protected JsonRPC jsonRPC;
    private JsonRemoteJob followUpJob;

    protected JsonRPC startJsonRPC() {
        this.jsonRPC = new JsonRPC();
        return jsonRPC;
    }

    @Override
    public RemoteMessage getMessage() {
        return getJsonMessage(startJsonRPC());
    }

    protected JSONObject getReturnValue(String message) throws IOException, JSONException {
        return jsonRPC.getReturnValue(message);
    }

    protected void setFollowUpJob(JsonRemoteJob followUpJob) {
        this.followUpJob = followUpJob;
    }

    public JsonRemoteJob getFollowUpJob() {
        return followUpJob;
    }

    abstract protected RemoteMessage getJsonMessage(JsonRPC jsonRPC);
    abstract protected Result handleJson(JSONObject returnValue, InputStream binaryData);

    static public Result run(JsonRemoteJob job, IRemoteRequest remoteRequest, IErrorCallback errorHandler)
            throws IOException, JSONException {
        RemoteMessage response = remoteRequest.send(job.getMessage());
        JSONObject returnValue = job.getReturnValue(response.message);
        Result result = job.handleJson(returnValue, response.binaryData);
        if (result.status == Result.ERROR) {
            if (errorHandler != null)
                errorHandler.onError(returnValue, response.binaryData);
        } else if (result.status == Result.FOLLOW_UP_JOB)
            return run(job.getFollowUpJob(), remoteRequest, errorHandler);
        return result;
    }
}
