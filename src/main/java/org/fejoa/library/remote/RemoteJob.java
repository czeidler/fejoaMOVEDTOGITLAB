/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;


public class RemoteJob<T> {
    static public class Result {
        final public int status;
        final public String message;

        public Result(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    protected IRemoteRequest remoteRequest;

    public T run(IRemoteRequest remoteRequest) throws Exception {
        this.remoteRequest = remoteRequest;
        return null;
    }
}
