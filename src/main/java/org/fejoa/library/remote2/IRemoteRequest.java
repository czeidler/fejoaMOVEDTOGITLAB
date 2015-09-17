/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import java.io.IOException;


public interface IRemoteRequest {
    RemoteJob.Result send(RemoteJob job) throws IOException;
    void cancel();
}
