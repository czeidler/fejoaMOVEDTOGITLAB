/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library2.Client;
import org.fejoa.library2.Remote;
import org.fejoa.library2.remote.*;
import org.fejoa.library2.util.LooperThread;
import org.fejoa.server.JettyServer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;


public class ClientTest extends TestCase {
    final static String TEST_DIR = "jettyTest";
    final static String SERVER_TEST_DIR = TEST_DIR + "/Server";
    final static String SERVER_URL = "http://localhost:8080/";
    final static String USER_NAME = "testUser";
    final static String PASSWORD = "password";

    final private List<String> cleanUpDirs = new ArrayList<>();
    private JettyServer server;
    private Client client;
    private LooperThread clientThread = new LooperThread(10);
    private Semaphore finishedSemaphore = new Semaphore(0);

    class SimpleObserver implements Task.IObserver<Void, RemoteJob.Result> {
        final private Runnable onSuccess;

        public SimpleObserver(Runnable onSuccess) {
            this.onSuccess = onSuccess;

        }
        @Override
        public void onProgress(Void aVoid) {
            System.out.println("onProgress: ");
        }

        @Override
        public void onResult(RemoteJob.Result result) {
            if (result.status != RemoteJob.Result.DONE)
                fail(result.message);
            System.out.println("onNext: " + result.message);
            onSuccess.run();
        }

        @Override
        public void onException(Exception exception) {
            fail(exception.getMessage());
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        cleanUpDirs.add(TEST_DIR);
        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));

        server = new JettyServer(SERVER_TEST_DIR);
        server.start();

        client = new Client(TEST_DIR);
        client.getConnectionManager().setStartScheduler(new Task.NewThreadScheduler());
        client.getConnectionManager().setObserverScheduler(new Task.LooperThreadScheduler(clientThread));

        clientThread.start();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        server.stop();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private void onAccountCreated() throws Exception {
        System.out.print("Account Created");

        client.create(USER_NAME, SERVER_URL, PASSWORD);
        client.commit();

        // watch
        Remote defaultRemote = client.getUserData().getRemoteList().getDefault();
        final SyncManager syncManager = new SyncManager(client.getContext(), client.getConnectionManager(), defaultRemote);
        syncManager.startWatching(client.getUserData().getStorageRefList().getEntries(),
                new Task.IObserver<TaskUpdate, Void>() {
                    @Override
                    public void onProgress(TaskUpdate update) {
                        System.out.println(update.toString());
                        if (update.getProgress() == update.getTotalWork())
                            syncManager.stopWatching();
                    }

                    @Override
                    public void onResult(Void aVoid) {
                        System.out.println("sync ok");
                        finish();
                    }

                    @Override
                    public void onException(Exception exception) {
                        fail();
                    }
                });
    }

    private void finish() {
        finishedSemaphore.release();
    }

    public void testClient() throws Exception {
        clientThread.post(new Runnable() {
            @Override
            public void run() {
                client.createAccount(USER_NAME, PASSWORD, SERVER_URL, new SimpleObserver(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            onAccountCreated();
                        } catch (Exception e) {
                            fail(e.getMessage());
                        }
                    }
                }));
            }
        });

        finishedSemaphore.acquire();
        clientThread.quit(true);
    }
}
