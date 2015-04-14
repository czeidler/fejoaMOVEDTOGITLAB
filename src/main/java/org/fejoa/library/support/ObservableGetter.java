/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;


import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscription;
import rx.concurrency.Schedulers;

import java.util.ArrayList;
import java.util.List;


public abstract class ObservableGetter<T> {
    public T getSync() throws Exception {
        final List<T> result = new ArrayList<>(1);
        final Exception[] exception = {null};
        get(Schedulers.currentThread(), Schedulers.currentThread()).subscribe(new Observer<T>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                exception[0] = (Exception)e;
            }

            @Override
            public void onNext(T args) {
                result.add(args);
            }
        });
        if (exception[0] != null)
            throw exception[0];
        return result.get(0);
    }

    public Observable<T> get(Scheduler observerOn) {
        return get(Schedulers.currentThread(), observerOn);
    }

    public Observable<T> get(final Scheduler subscribeOn, final Scheduler observerOn) {
        T result = getCached();
        if (result != null)
            return Observable.from(result);

        return Observable.create(new Observable.OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(Observer<? super T> observer) {
                createWorker().subscribeOn(subscribeOn).observeOn(observerOn).subscribe(observer);
                return new Subscription() {
                    @Override
                    public void unsubscribe() {
                        cancelWorker();
                    }
                };
            }
        });
    }

    abstract protected Observable<T> createWorker();
    protected void cancelWorker() {

    }

    protected T getCached() {
        return null;
    }
}
