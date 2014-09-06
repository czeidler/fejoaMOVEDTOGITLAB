package org.fejoa.library.remote;


import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscription;
import rx.concurrency.SwingScheduler;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func2;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class MyGenericScheduledExecutorService {

    private static final String THREAD_NAME_PREFIX = "RxScheduledExecutorPool-";

    private final static MyGenericScheduledExecutorService INSTANCE = new MyGenericScheduledExecutorService();
    private final ScheduledExecutorService executor;

    private MyGenericScheduledExecutorService() {
        int count = Runtime.getRuntime().availableProcessors();
        if (count > 4) {
            count = count / 2;
        }
        // we don't need more than 8 to handle just scheduling and doing no work
        if (count > 8) {
            count = 8;
        }
        executor = Executors.newScheduledThreadPool(count, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                return new Thread("MyGenericScheduledExecutorService");
            }
        });
    }

    /**
     * See class Javadoc for information on what this is for and how to use.
     *
     * @return {@link ScheduledExecutorService} for generic use.
     */
    public static ScheduledExecutorService getInstance() {
        return INSTANCE.executor;
    }
}


public class RequestQueue {
    interface IIdleJob {
        public Observable getObservable();
        public Observer getObserver();
    }

    private static class EventLoopScheduler extends Scheduler {
        private final ExecutorService executor;

        private EventLoopScheduler() {
            executor = Executors.newSingleThreadExecutor();
        }

        @Override
        public <T> Subscription schedule(final T state, final Func2<? super Scheduler, ? super T, ? extends Subscription> action) {
            final Scheduler _scheduler = this;
            return Subscriptions.from(executor.submit(new Runnable() {

                @Override
                public void run() {
                    action.call(_scheduler, state);
                }
            }));
        }

        @Override
        public <T> Subscription schedule(final T state, final Func2<? super Scheduler, ? super T, ? extends Subscription> action, final long delayTime, final TimeUnit unit) {
            // we will use the system scheduler since it doesn't make sense to launch a new Thread and then sleep
            // we will instead schedule the event then launch the thread after the delay has passed
            final Scheduler _scheduler = this;
            final CompositeSubscription subscription = new CompositeSubscription();
            ScheduledFuture<?> f = MyGenericScheduledExecutorService.getInstance().schedule(new Runnable() {

                @Override
                public void run() {
                    if (!subscription.isUnsubscribed()) {
                        // when the delay has passed we now do the work on the actual scheduler
                        Subscription s = _scheduler.schedule(state, action);
                        // add the subscription to the CompositeSubscription so it is unsubscribed
                        subscription.add(s);
                    }
                }
            }, delayTime, unit);

            // add the ScheduledFuture as a subscription so we can cancel the scheduled action if an unsubscribe happens
            subscription.add(Subscriptions.create(f));

            return subscription;
        }
    }

    private int queueSize = 0;

    final private IIdleJob idleJob;
    private Subscription idleJobSubscription;

    final private EventLoopScheduler scheduler = new EventLoopScheduler();

    public RequestQueue(IIdleJob idleJob) {
        this.idleJob = idleJob;
        start();
    }

    private void start() {
        if (idleJob != null)
            idleJobSubscription = queueJobLocked(idleJob.getObservable(), idleJob.getObserver());
    }

    class RequestObserver<T> implements Observer<T> {
        private Observer<T> child;

        public RequestObserver(Observer<T> child) {

        }

        @Override
        public void onCompleted() {
            child.onCompleted();

            jobFinished();
        }

        @Override
        public void onError(Throwable e) {
            child.onError(e);

            jobFinished();
        }

        @Override
        public void onNext(T args) {
            child.onNext(args);
        }
    }

    protected <T> Observable<T> config(Observable<T> observable) {
        return observable.subscribeOn(scheduler)
                .observeOn(SwingScheduler.getInstance());
    }

    private final Lock queueLock = new ReentrantLock();

    private void jobFinished() {
        try {
            queueLock.lock();
            queueSize--;
            // if no job is running start the idle job
            if (queueSize == 0 && idleJob != null) {
                idleJobSubscription = null;
                idleJobSubscription = queueJob(idleJob.getObservable(), idleJob.getObserver());
            }

        } finally {
            queueLock.unlock();
        }
    }

    private <T> Subscription queueJob(Observable<T> observable, Observer<? super T> observer) {
        if (queueSize == 1 && idleJobSubscription != null) {
            idleJobSubscription.unsubscribe();
            idleJobSubscription = null;
        }

        queueSize++;
        Observable<T> configuredObservable = config(observable);
        return configuredObservable.subscribe(new RequestObserver<>(observer));
    }

    private <T> Subscription queueJobLocked(Observable<T> observable, Observer<? super T> observer) {
        try {
            queueLock.lock();

            return queueJob(observable, observer);
        } finally {
            queueLock.unlock();
        }
    }

    public <T> Observable<T> queue(final Observable<T> observable) {
        return Observable.create(new Observable.OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(Observer<? super T> observer) {
                return queueJobLocked(observable, observer);
            }
        });
    }
}
