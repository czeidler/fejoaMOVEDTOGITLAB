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
import rx.concurrency.SwingScheduler;


public class FejoaSchedulers {
    static public <T> Subscription subscribeOnIOToMain(Observable<T> observable, Observer<T> observer) {
        return observable.subscribeOn(ioScheduler())
                .observeOn(mainGuiScheduler())
                .subscribe(observer);
    }

    static public Scheduler ioScheduler() {
        return Schedulers.threadPoolForIO();
    }

    static public Scheduler remoteScheduler() {
        return Schedulers.threadPoolForIO();
    }

    static public Scheduler mainGuiScheduler() {
        return SwingScheduler.getInstance();
    }

    static public Scheduler currentThreadScheduler() {
        return Schedulers.currentThread();
    }
}
