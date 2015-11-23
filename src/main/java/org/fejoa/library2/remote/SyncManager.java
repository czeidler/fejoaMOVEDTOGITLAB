/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.Remote;
import org.fejoa.library2.Storage;

import java.util.Collection;
import java.util.List;


public class SyncManager {
    final private FejoaContext context;
    final private ConnectionManager connectionManager;
    final private Remote remote;

    public SyncManager(FejoaContext context, ConnectionManager connectionManager, Remote remote) {
        this.context = context;
        this.connectionManager = connectionManager;
        this.remote = remote;
    }

    public void sync(List<Storage> storages) {

    }

    public void watch(Collection<Storage> storages, Task.IObserver<Void, WatchJob.Result> observer) {
        connectionManager.submit(new WatchJob(context, remote.getUser(), storages),
                new ConnectionManager.ConnectionInfo(remote.getUser(), remote.getServer()),
                new ConnectionManager.AuthInfo(ConnectionManager.AuthInfo.ROOT, null),
                observer);
    }

    public void startWatching(List<Storage> storages) {

    }
}
