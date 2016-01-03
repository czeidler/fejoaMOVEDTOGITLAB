/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import java.io.IOException;


public class RemoteJob<T> {
    static public class Result {
        final static public int EXCEPTION = -3;
        final static public int ERROR = -2;
        final static public int FOLLOW_UP_JOB = -1;
        final static public int DONE = 0;

        final public int status;
        final public String message;

        public Result(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    protected IRemoteRequest remoteRequest;

    public T run(IRemoteRequest remoteRequest) throws IOException, Exception {
        this.remoteRequest = remoteRequest;
        return null;
    }
}
