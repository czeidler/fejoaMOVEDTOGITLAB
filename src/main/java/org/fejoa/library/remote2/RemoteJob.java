/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;


import java.io.InputStream;
import java.io.OutputStream;

abstract public class RemoteJob {
    static public class Result {
        final static public int ERROR = -1;
        final static public int DONE = 0;
        final static public int FOLLOW_UP_JOB = 1;

        final public int status;
        final public String message;

        public Result(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private boolean hasData = false;

    public RemoteJob(boolean hasData) {
        this.hasData = hasData;
    }

    public boolean hasData() {
        return hasData;
    }

    abstract public String getHeader();
    abstract public void writeData(OutputStream outputStream);
    abstract public Result handleResponse(String header, InputStream inputStream);
}
