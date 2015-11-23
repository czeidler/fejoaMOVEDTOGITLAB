/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;


public class JsonRemoteJob<T extends RemoteJob.Result> extends RemoteJob<T> {
    final static public String ACCESS_DENIED_KEY = "access_denied";

    /**
     * Can be used for errors unrelated to the current job. For example, token expired or auth became invalid.
     */
    public interface IErrorCallback {
         void onError(JSONObject returnValue, InputStream binaryData);
    }

    protected JsonRPC jsonRPC;
    private JsonRemoteJob<T> followUpJob;
    protected IErrorCallback errorCallback;

    protected JsonRPC startNewJsonRPC() {
        this.jsonRPC = new JsonRPC();
        return jsonRPC;
    }

    protected JSONObject getReturnValue(String message) throws IOException, JSONException {
        return jsonRPC.getReturnValue(message);
    }

    protected Result getResult(JSONObject returnValue) {
        int status = Result.ERROR;
        String message;
        try {
            status = returnValue.getInt("status");
            message = returnValue.getString("message");
        } catch (Exception e) {
            e.printStackTrace();
            message = e.getMessage();
        }
        return new Result(status, message);
    }

    protected void setFollowUpJob(JsonRemoteJob<T> followUpJob) {
        this.followUpJob = followUpJob;
    }

    public JsonRemoteJob<T> getFollowUpJob() {
        return followUpJob;
    }

    public void setErrorCallback(IErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
    }

    @Override
    public T run(IRemoteRequest remoteRequest) throws IOException {
        super.run(remoteRequest);
        startNewJsonRPC();
        return null;
    }

    static public <T extends Result> T run(JsonRemoteJob<T> job, IRemoteRequest remoteRequest,
                                           IErrorCallback errorHandler)
            throws IOException, JSONException {
        job.setErrorCallback(errorHandler);
        T result = job.run(remoteRequest);
        if (result.status == Result.FOLLOW_UP_JOB) {
            System.out.println("Start follow up job (" + job.getFollowUpJob().getClass().getSimpleName() + ") after: "
                    + result.message);
            return run(job.getFollowUpJob(), remoteRequest, errorHandler);
        }
        return result;
    }
}
