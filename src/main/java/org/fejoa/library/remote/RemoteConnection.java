/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;


public class RemoteConnection {
    final ConnectionManager manager;
    final ConnectionInfo info;

    public RemoteConnection(ConnectionManager manager, ConnectionInfo info) {
        this.manager = manager;
        this.info = info;
    }

    private Observable<IRemoteRequest> getPreparedRemoteRequest() {
        return manager.getPreparedRemoteRequest(info);
    }

    public <T> Observable<T> queueObservable(Observable<T> observable) {
        return manager.queueJob(info, observable);
    }

    private Observable<byte[]> sendBytes(final byte[] data) {
        Observable<byte[]> out = getPreparedRemoteRequest().mapMany(
                new Func1<IRemoteRequest, Observable<byte[]>>() {
                    @Override
                    public Observable<byte[]> call(IRemoteRequest remoteRequest) {
                        return RemoteRequestHelper.send(remoteRequest, data);
                    }
                });
        return out;
    }

    class CurrentSubscription implements Subscription {
        public Subscription child = null;

        @Override
        public void unsubscribe() {
            // child could be set to null from a different thread so copy it to a local variable
            Subscription currentChild = child;
            if (currentChild != null)
                currentChild.unsubscribe();
        }
    };

    public Observable<RemoteConnectionJob.Result> queueJob(final RemoteConnectionJob job) {
        return queueObservable(runJob(job));
    }

    public Observable<RemoteConnectionJob.Result> runJob(final RemoteConnectionJob job) {
        return Observable.create(new Observable.OnSubscribeFunc<RemoteConnectionJob.Result>() {
            @Override
            public Subscription onSubscribe(final Observer<? super RemoteConnectionJob.Result> observer) {
                final byte[] request;
                try {
                    request = job.getRequest();
                } catch (Exception e) {
                    e.printStackTrace();
                    observer.onError(e);
                    return Subscriptions.empty();
                }

                final CurrentSubscription currentSubscription = new CurrentSubscription();
                currentSubscription.child = sendBytes(request).subscribe(new Observer<byte[]>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        observer.onError(e);
                    }

                    @Override
                    public void onNext(byte[] reply) {
                        try {
                            RemoteConnectionJob.Result result = job.handleResponse(reply);
                            observer.onNext(result);
                            RemoteConnectionJob followUpJob = job.getFollowUpJob();
                            if (followUpJob == null || result.status < RemoteConnectionJob.Result.DONE) {
                                observer.onCompleted();
                                return;
                            }
                            currentSubscription.child = runJob(followUpJob).subscribe(observer);
                        } catch (Exception e) {
                            e.printStackTrace();
                            observer.onError(e);
                            return;
                        }
                    }
                });

                return currentSubscription;
            }
        });
    }
}
