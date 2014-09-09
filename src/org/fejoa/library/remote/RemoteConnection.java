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
import rx.util.functions.Func1;


public class RemoteConnection {
    final ConnectionManager manager;
    final ConnectionInfo info;

    public RemoteConnection(ConnectionManager manager, ConnectionInfo info) {
        this.manager = manager;
        this.info = info;
    }

    public Observable<IRemoteRequest> getPreparedRemoteRequest() {
        return manager.getPreparedRemoteRequest(info);
    }

    public <T> Observable<T> queueObservable(Observable<T> observable) {
        return manager.queueJob(info, observable);
    }

    public Observable<byte[]> queueBytes(final byte[] data) {
        Observable<byte[]> out = getPreparedRemoteRequest().mapMany(
                new Func1<IRemoteRequest, Observable<byte[]>>() {
                    @Override
                    public Observable<byte[]> call(IRemoteRequest remoteRequest) {
                        return remoteRequest.send(data);
                    }
                });
        return queueObservable(out);
    }

    public Observable<RemoteConnectionJob.Result> queueJob(final RemoteConnectionJob job) {
        return Observable.create(new Observable.OnSubscribeFunc<RemoteConnectionJob.Result>() {
            @Override
            public Subscription onSubscribe(final Observer<? super RemoteConnectionJob.Result> observer) {
                final byte[] request;
                try {
                    request = job.getRequest();
                } catch (Exception e) {
                    e.printStackTrace();
                    observer.onError(e);
                    return null;
                }

                queueBytes(request).subscribe(new Observer<byte[]>() {
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
                            String test = new String(reply);
                            System.out.print(test);
                            RemoteConnectionJob.Result result = job.handleResponse(reply);
                            RemoteConnectionJob followUpJob = job.getFollowUpJob();
                            if (result.status != RemoteConnectionJob.Result.CONTINUE || followUpJob == null) {
                                observer.onNext(result);
                                observer.onCompleted();
                                return;
                            }
                            queueJob(followUpJob).subscribe(observer);
                        } catch (Exception e) {
                            e.printStackTrace();
                            observer.onError(e);
                            return;
                        }
                    }
                });

                return null;
            }
        });
    }
}
