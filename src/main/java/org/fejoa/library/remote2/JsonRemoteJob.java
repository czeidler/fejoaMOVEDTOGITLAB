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
    public JsonRemoteJob(boolean hasData) {
        super(hasData);
    }

    /**
     * Can be used for errors unrelated to the current job. For example, token expired or auth became invalid.
     */
    public interface IErrorCallback {
         void onError(JSONObject returnValue, InputStream binaryData);
    }

    protected JsonRPC jsonRPC;
    private JsonRemoteJob followUpJob;
    private IErrorCallback errorCallback;

    protected JsonRPC startJsonRPC() {
        this.jsonRPC = new JsonRPC();
        return jsonRPC;
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

    public void setErrorCallback(IErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
    }

    @Override
    public String getHeader() {
        return getJsonHeader(startJsonRPC());
    }

    abstract public String getJsonHeader(JsonRPC jsonRPC);

    @Override
    public Result handleResponse(String header, InputStream inputStream) {
        JSONObject returnValue;
        try {
            returnValue = getReturnValue(header);
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(Result.ERROR, e.getMessage());
        }
        Result result = handleJson(returnValue, inputStream);
        if (result.status == Result.ERROR && errorCallback != null)
            errorCallback.onError(returnValue, inputStream);
        return result;
    }

    abstract protected Result handleJson(JSONObject returnValue, InputStream binaryData);

    static public Result run(JsonRemoteJob job, IRemoteRequest remoteRequest, IErrorCallback errorHandler)
            throws IOException, JSONException {
        job.setErrorCallback(errorHandler);
        Result result = remoteRequest.send(job);
        if (result.status == Result.FOLLOW_UP_JOB)
            return run(job.getFollowUpJob(), remoteRequest, errorHandler);
        return result;
    }
}
