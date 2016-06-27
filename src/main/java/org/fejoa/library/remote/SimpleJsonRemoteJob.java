/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


abstract public class SimpleJsonRemoteJob<T extends RemoteJob.Result> extends JsonRemoteJob<T> {
    private boolean hasData = false;

    public SimpleJsonRemoteJob(boolean hasData) {
        this.hasData = hasData;
    }

    public boolean hasData() {
        return hasData;
    }

    public String getHeader() throws Exception {
        return getJsonHeader(jsonRPC);
    }

    private T handleResponse(String header, InputStream inputStream) throws IOException {
        JSONObject returnValue;
        try {
            returnValue = getReturnValue(header);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }

        return handleJson(returnValue, inputStream);
    }

    abstract public String getJsonHeader(JsonRPC jsonRPC) throws Exception;
    abstract protected T handleJson(JSONObject returnValue, InputStream binaryData);

    public void writeData(OutputStream outputStream) throws IOException {

    }

    @Override
    public T run(IRemoteRequest remoteRequest) throws Exception {
        super.run(remoteRequest);
        OutputStream outputStream = remoteRequest.open(getHeader(), hasData());
        if (hasData())
            writeData(outputStream);
        return handleResponse(remoteRequest.receiveHeader(), remoteRequest.receiveData());
    }
}
