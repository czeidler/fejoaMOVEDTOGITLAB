package org.fejoa.library.remote;


import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscription;
import rx.concurrency.Schedulers;
import rx.concurrency.SwingScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


class RequestQueue {
    public interface IIdleJob {
        public Observable getObservable();
        public Observer getObserver();
    }

    class QueueEntry <T> {
        public Observable<T> observable;
        public RequestObserver<T> observer;
        public Subscription subscription;
    }

    class RequestObserver<T> implements Observer<T> {
        private Observer<? super T> child;

        public RequestObserver(Observer<? super T> child) {
            this.child = child;
        }

        @Override
        public void onCompleted() {
            child.onCompleted();

            onJobFinished();
        }

        @Override
        public void onError(Throwable e) {
            child.onError(e);

            onJobFinished();
        }

        @Override
        public void onNext(T args) {
            child.onNext(args);
        }
    }

    private List<QueueEntry> queue = new ArrayList<>();
    private QueueEntry runningEntry = null;
    private final Lock queueLock = new ReentrantLock();

    private IIdleJob idleJob;
    private Subscription idleJobSubscription;

    public RequestQueue(IIdleJob idleJob) {
        setIdleJob(idleJob);
    }

    public <T> Observable<T> queue(final Observable<T> observable) {
        final QueueEntry<T> entry = new QueueEntry<>();
        entry.observable = observable;
        return Observable.create(new Observable.OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(Observer<? super T> observer) {
                entry.observer = new RequestObserver<>(observer);

                queueEntry(entry);
                return new Subscription() {
                    @Override
                    public void unsubscribe() {
                        onUnsubscribe(entry);
                    }
                };
            }
        });
    }

    public void setIdleJob(IIdleJob job) {
        try {
            queueLock.lock();
            this.idleJob = job;

            if (idleJobSubscription != null) {
                stopIdleJob();
            } else
                schedule();

        } finally {
            queueLock.unlock();
        }
    }

    private Scheduler subscribeScheduler() {
        return Schedulers.threadPoolForComputation();
    }

    private Scheduler observeScheduler() {
        return SwingScheduler.getInstance();
    }

    private void stopIdleJob() {
        idleJobSubscription.unsubscribe();
        idleJobSubscription = null;
        onJobFinished();
    }

    private <T> void runEntry(QueueEntry<T> entry) {
        entry.subscription = entry.observable.subscribeOn(subscribeScheduler()).observeOn(observeScheduler())
                .subscribe(entry.observer);
        runningEntry = entry;
    }

    private void onJobFinished() {
        try {
            queueLock.lock();
            runningEntry = null;
            schedule();
        } finally {
            queueLock.unlock();
        }
    }

    private <T> void runIdleJob() {
        QueueEntry<T> entry = new QueueEntry<>();
        entry.observable = idleJob.getObservable();
        entry.observer = new RequestObserver<T>(idleJob.getObserver());
        runEntry(entry);
        idleJobSubscription = entry.subscription;
    }

    private void schedule() {
        if (idleJobSubscription == null && runningEntry != null)
            return;
        if (queue.size() == 0) {
            if (idleJob == null)
                return;
            if (idleJobSubscription == null)
                runIdleJob();
            return;
        }

        // if idle job is running just cancel it, that will trigger working the queue
        if (idleJobSubscription != null) {
            stopIdleJob();
            return;
        }

        // non-empty queue start entry
        QueueEntry entry = queue.remove(0);
        runEntry(entry);
    }

    private <T> void queueEntry(QueueEntry<T> entry) {
        try {
            queueLock.lock();
            queue.add(entry);
            schedule();
        } finally {
            queueLock.unlock();
        }
    }

    private <T> void onUnsubscribe(QueueEntry<T> entry) {
        try {
            queueLock.lock();

            if (entry.subscription == null) {
                queue.remove(entry);
                schedule();
                return;
            } else {
                entry.subscription.unsubscribe();
                onJobFinished();
            }

        } finally {
            queueLock.unlock();
        }
    }

    public void shutdown(boolean force) {
        setIdleJob(null);
        if (!force)
            return;
        try {
            queueLock.lock();
            queue.clear();
            if (runningEntry != null)
                runningEntry.subscription.unsubscribe();
        } finally {
            queueLock.unlock();
        }
    }

}