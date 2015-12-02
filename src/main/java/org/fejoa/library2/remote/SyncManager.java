/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.fejoa.library.database.JGitInterface;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.Remote;
import org.fejoa.library2.Storage;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.util.*;


public class SyncManager {
    final private FejoaContext context;
    final private ConnectionManager connectionManager;
    final private Remote remote;
    private Task.ICancelFunction watchFunction;
    final private Map<String, Task.ICancelFunction> ongoingSyncJobs = new HashMap<>();

    public SyncManager(FejoaContext context, ConnectionManager connectionManager, Remote remote) {
        this.context = context;
        this.connectionManager = connectionManager;
        this.remote = remote;
    }

    private void sync(List<String> storageIdList, final Task.IObserver<TaskUpdate, Void> observer) {
        if (ongoingSyncJobs.size() != 0)
            return;

        // add the ids in case the job finishes before submit returns, e.g. if executed immediately
        for (String id : storageIdList) {
            if (!ongoingSyncJobs.containsKey(id))
                ongoingSyncJobs.put(id, null);
        }

        for (String id : storageIdList)
            sync(id, storageIdList.size(), observer);
    }

    private void watch(Collection<Storage> storages, Task.IObserver<Void, WatchJob.Result> observer) {
        if (watchFunction != null)
            return;
        watchFunction = connectionManager.submit(new WatchJob(context, remote.getUser(), storages),
                new ConnectionManager.ConnectionInfo(remote.getUser(), remote.getServer()),
                new ConnectionManager.AuthInfo(ConnectionManager.AuthInfo.ROOT, null),
                observer);
    }

    public void startWatching(final Collection<Storage> storageList, final Task.IObserver<TaskUpdate, Void> observer) {
        final Task.IObserver<Void, WatchJob.Result> watchObserver = new Task.IObserver<Void, WatchJob.Result>() {
            private TaskUpdate makeUpdate(String message) {
                return new TaskUpdate("Watching", -1, -1, message);
            }

            @Override
            public void onProgress(Void aVoid) {

            }

            @Override
            public void onResult(WatchJob.Result result) {
                // timeout?
                if (result.updated.size() == 0) {
                    watch(storageList, this);
                    observer.onProgress(makeUpdate("timeout"));
                    return;
                }

                observer.onProgress(makeUpdate("start syncing"));
                final Task.IObserver<Void, WatchJob.Result> that = this;
                sync(result.updated, new Task.IObserver<TaskUpdate, Void>() {
                    @Override
                    public void onProgress(TaskUpdate update) {
                        observer.onProgress(update);
                    }

                    @Override
                    public void onResult(Void result) {
                        // still watching?
                        if (watchFunction != null)
                            watch(storageList, that);
                        else
                            observer.onResult(null);
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                    }
                });
            }

            @Override
            public void onException(Exception exception) {
                // if we haven't stopped watching this is an real exception
                if (watchFunction != null)
                    observer.onException(exception);
                else
                    observer.onResult(null);
            }
        };

        watch(storageList, watchObserver);
    }

    public void stopWatching() {
        for (Map.Entry<String, Task.ICancelFunction> entry : ongoingSyncJobs.entrySet()) {
            if (entry.getValue() == null)
                continue;
            entry.getValue().cancel();
        }
        ongoingSyncJobs.clear();
        if (watchFunction != null) {
            watchFunction.cancel();
            watchFunction = null;
        }
    }

    private void sync(final String id, final int nJobs, final Task.IObserver<TaskUpdate, Void> observer) {
        final StorageDir dir;
        try {
            dir = context.getStorage(id);
        } catch (IOException e) {
            e.printStackTrace();
            observer.onException(e);
            ongoingSyncJobs.remove(id);
            return;
        }

        final ConnectionManager.ConnectionInfo connectionInfo = new ConnectionManager.ConnectionInfo(remote.getUser(),
                remote.getServer());
        final ConnectionManager.AuthInfo authInfo = new ConnectionManager.AuthInfo(ConnectionManager.AuthInfo.ROOT,
                null);
        final JGitInterface gitInterface = (JGitInterface)dir.getDatabase();
        // pull
        final Task.ICancelFunction job = connectionManager.submit(new GitPullJob(gitInterface.getRepository(),
                remote.getUser(), dir.getBranch()),
                connectionInfo, authInfo,
                new Task.IObserver<Void, GitPullJob.Result>() {
                    @Override
                    public void onProgress(Void aVoid) {
                        //observer.onProgress(aVoid);
                    }

                    @Override
                    public void onResult(GitPullJob.Result result) {
                        try {
                            dir.merge(result.pulledRev);
                            String tip = dir.getTip();
                            if (tip.equals(result.pulledRev)) {
                                jobFinished(id, observer, nJobs, "sync after pull: " + id);
                                return;
                            }
                        } catch (IOException e) {
                            observer.onException(e);
                        }

                        // push
                        connectionManager.submit(new GitPushJob(gitInterface.getRepository(), remote.getUser(),
                                gitInterface.getBranch()), connectionInfo, authInfo,
                                new Task.IObserver<Void, RemoteJob.Result>() {
                            @Override
                            public void onProgress(Void aVoid) {
                                //observer.onProgress(aVoid);
                            }

                            @Override
                            public void onResult(RemoteJob.Result result) {
                                jobFinished(id, observer, nJobs, "sync after push: " + id);
                            }

                            @Override
                            public void onException(Exception exception) {
                                observer.onException(exception);
                                jobFinished(id, observer, nJobs, "exception");
                            }
                        });
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                        jobFinished(id, observer, nJobs, "exception");
                    }
                });

        // only add the job if the
        if (ongoingSyncJobs.containsKey(id))
            ongoingSyncJobs.put(id, job);
    }

    private void jobFinished(String id, Task.IObserver<TaskUpdate, Void> observer, int totalNumberOfJobs,
                             String message) {
        ongoingSyncJobs.remove(id);

        int remainingJobs = ongoingSyncJobs.size();
        observer.onProgress(new TaskUpdate("Sync", totalNumberOfJobs, totalNumberOfJobs - remainingJobs, message));
        if (remainingJobs == 0)
            observer.onResult(null);
    }
}
