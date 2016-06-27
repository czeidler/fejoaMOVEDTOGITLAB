/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.support.Task;


public class TaskTest extends TestCase {
    public void testBasics() {
        final Float[] progress = new Float[1];
        final Boolean[] result = new Boolean[1];
        final Boolean[] canceled = new Boolean[1];
        final Boolean[] exceptionReceived = new Boolean[1];

        result[0] = false;
        canceled[0] = false;
        exceptionReceived[0] = false;

        final float PROGRESS = 0.5f;

        Task<Float, Boolean> task = new Task<>(new Task.ITaskFunction<Float, Boolean>() {
            @Override
            public void run(Task<Float, Boolean> task) throws Exception {
                task.onProgress(PROGRESS);
                Thread.sleep(1000);
                assertEquals(true, (boolean)canceled[0]);
                task.onResult(true);
                throw new Exception("Test Exception");
            }

            @Override
            public void cancel() {
                canceled[0] = true;
            }
        });
        task.setStartScheduler(new Task.NewThreadScheduler());
        task.setObserverScheduler(new Task.CurrentThreadScheduler());
        task.start(new Task.IObserver<Float, Boolean>() {
            @Override
            public void onProgress(Float aFloat) {
                progress[0] = aFloat;
            }

            @Override
            public void onResult(Boolean aBoolean) {
                result[0] = aBoolean;
            }

            @Override
            public void onException(Exception exception) {
                exceptionReceived[0] = true;
            }
        });
        task.cancel();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals(PROGRESS, progress[0]);
        assertEquals(true, (boolean)result[0]);
        assertEquals(true, (boolean)exceptionReceived[0]);
    }
}
