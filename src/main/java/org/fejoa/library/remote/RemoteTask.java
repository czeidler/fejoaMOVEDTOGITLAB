/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;


class RemoteTask {
    final protected ConnectionManager connectionManager;

    protected RemoteTask(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
}
