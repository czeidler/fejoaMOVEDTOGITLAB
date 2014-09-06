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
        final public boolean done;
        final public String message;

        public Result(boolean done) {
            this.done = done;
            this.message = "";
        }

        public Result(boolean done, String message) {
            this.done = done;
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
