/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;


public class SyncResultData extends RemoteConnectionJob.Result.ResultData {
    final public String remoteUid;
    final public String branch;
    final public String syncedTip;

    protected SyncResultData(String remoteUid, String branch, String syncedTip) {
        this.remoteUid = remoteUid;
        this.branch = branch;
        this.syncedTip = syncedTip;
    }
}
