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
import org.fejoa.server.JettyServer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class ClientTest extends TestCase {
    final static String TEST_DIR = "jettyTest";
    final static String SERVER_TEST_DIR = TEST_DIR + "/Server";
    final static String SERVER_URL = "http://localhost:8080/";
    final static String USER_NAME = "testUser";
    final static String PASSWORD = "password";

    final List<String> cleanUpDirs = new ArrayList<>();
    JettyServer server;

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

        server = new JettyServer(SERVER_TEST_DIR);
        server.start();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        server.stop();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private void onAccountCreated() {
        System.out.print("Account Created");
    }

    public void testClient() throws Exception {
        org.fejoa.library2.Client client = new Client(TEST_DIR);
        client.getConnectionManager().setStartScheduler(new Task.CurrentThreadScheduler());
        client.createAccount(USER_NAME, PASSWORD, SERVER_URL, new SimpleObserver(new Runnable() {
            @Override
            public void run() {
                onAccountCreated();
            }
        }));

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
            }

            @Override
            public void onResult(Void aVoid) {
                System.out.println("sync ok");
            }

            @Override
            public void onException(Exception exception) {
                fail();
            }
        });
    }
}
