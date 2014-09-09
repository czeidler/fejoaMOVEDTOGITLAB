/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;


public abstract class RemoteConnectionJob {
    static public class Result {
        static public int DONE = 0;
        static public int CONTINUE = -1;
        static public int ERROR = -2;
        final public int status;
        final public String message;

        public Result(int status) {
            this.status = status;
            this.message = "";
        }

        public Result(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    // executed if job succeeded
    private RemoteConnectionJob followUpJob = null;

    public abstract byte[] getRequest() throws Exception;
    public abstract Result handleResponse(byte[] reply) throws Exception;

    public RemoteConnectionJob getFollowUpJob() {
        return followUpJob;
    }

    public void setFollowUpJob(RemoteConnectionJob followUpJob) {
        this.followUpJob = followUpJob;
    }
}
