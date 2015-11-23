/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;


public class Task<Update, Result> {
    public interface ICancelFunction {
        void cancel();
    }

    public interface ITaskFunction<Update, Result> extends ICancelFunction {
        void run(Task<Update, Result> task) throws Exception;
    }

    public interface IObserver<Update, Result> {
        void onProgress(Update update);
        void onResult(Result result);
        void onException(Exception exception);
    }

    public interface IScheduler {
        void run(Runnable runnable);
    }

    static public class NewThreadScheduler implements IScheduler {
        @Override
        public void run(Runnable runnable) {
            new Thread(runnable).start();
        }
    }

    static public class CurrentThreadScheduler implements IScheduler {
        @Override
        public void run(Runnable runnable) {
            runnable.run();
        }
    }

    private boolean canceled = false;
    final private ITaskFunction<Update, Result> taskFunction;
    private IObserver<Update, Result> observable;
    private IScheduler startScheduler = new NewThreadScheduler();
    private IScheduler observerScheduler = new CurrentThreadScheduler();

    public Task(ITaskFunction<Update, Result> taskFunction) {
        this.taskFunction = taskFunction;
    }

    public Task<Update, Result> setStartScheduler(IScheduler startScheduler) {
        this.startScheduler = startScheduler;
        return this;
    }

    public Task<Update, Result> setObserverScheduler(IScheduler observerScheduler) {
        this.observerScheduler = observerScheduler;
        return this;
    }

    public ICancelFunction start(IObserver<Update, Result> observable) {
        this.observable = observable;

        final Task<Update, Result> that = this;
        startScheduler.run(new Runnable() {
            @Override
            public void run() {
                try {
                    taskFunction.run(that);
                } catch(Exception e) {
                    onException(e);
                }
            }
        });
        return taskFunction;
    }

    public void cancel() {
        canceled = true;
        taskFunction.cancel();
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void onProgress(final Update update) {
        observerScheduler.run(new Runnable() {
            @Override
            public void run() {
                observable.onProgress(update);
            }
        });
    }

    public void onResult(final Result result) {
        observerScheduler.run(new Runnable() {
            @Override
            public void run() {
                observable.onResult(result);
            }
        });
    }

    public void onException(final Exception exception) {
        observerScheduler.run(new Runnable() {
            @Override
            public void run() {
                observable.onException(exception);
            }
        });
    }
}
