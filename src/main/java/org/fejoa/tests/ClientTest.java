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
import org.fejoa.library2.ContactPublic;
import org.fejoa.library2.Remote;
import org.fejoa.library2.command.ContactRequest;
import org.fejoa.library2.command.ContactRequestHandler;
import org.fejoa.library2.command.IncomingCommandManager;
import org.fejoa.library2.remote.*;
import org.fejoa.library2.util.LooperThread;
import org.fejoa.server.JettyServer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;


public class ClientTest extends TestCase {
    abstract class TestTask {
        TestTask nextTask;

        public void setNextTask(TestTask nextTask) {
            if (this.nextTask != null)
                fail("next task already set!");
            this.nextTask = nextTask;
        }

        protected void onTaskPerformed() {
            try {
                nextTask.perform(this);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        abstract protected void perform(TestTask previousTask) throws Exception;
    }

    private class ClientStatus {
        final public String name;
        public boolean firstSync;

        public ClientStatus(String name) {
            this.name = name;
        }
    }

    final static String TEST_DIR = "jettyTest";
    final static String SERVER_TEST_DIR = TEST_DIR + "/Server";
    final static String SERVER_URL = "http://localhost:8080/";
    final static String USER_NAME_1 = "testUser";
    final static String USER_NAME_2 = "testUser2";
    final static String PASSWORD = "password";

    final private List<String> cleanUpDirs = new ArrayList<>();
    private JettyServer server;
    private Client client1;
    private ClientStatus clientStatus1;
    private Client client2;
    private ClientStatus clientStatus2;
    private LooperThread clientThread = new LooperThread(10);

    private Semaphore finishedSemaphore;

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

        finishedSemaphore = new Semaphore(0);

        cleanUpDirs.add(TEST_DIR);
        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));

        server = new JettyServer(SERVER_TEST_DIR);
        server.start();

        client1 = new Client(TEST_DIR + "/" + USER_NAME_1);
        client1.getConnectionManager().setStartScheduler(new Task.NewThreadScheduler());
        client1.getConnectionManager().setObserverScheduler(new Task.LooperThreadScheduler(clientThread));
        clientStatus1 = new ClientStatus(USER_NAME_1);

        client2 = new Client(TEST_DIR + "/" + USER_NAME_2);
        client2.getConnectionManager().setStartScheduler(new Task.NewThreadScheduler());
        client2.getConnectionManager().setObserverScheduler(new Task.LooperThreadScheduler(clientThread));
        clientStatus2 = new ClientStatus(USER_NAME_2);

        clientThread.start();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        server.stop();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    class FinishTask extends TestTask {
        @Override
        protected void perform(TestTask previousTask) throws Exception {
            client1.stopSyncing();
            client2.stopSyncing();
            finishedSemaphore.release();
        }
    }

    class MergeTask extends TestTask {
        final private List<TestTask> tasks = new ArrayList<>();

        public MergeTask(TestTask... tasks) {
            for (TestTask task : tasks) {
                task.setNextTask(this);
                this.tasks.add(task);
            }
        }

        @Override
        protected void perform(TestTask previousTask) throws Exception {
            if (!tasks.remove(previousTask))
                throw new Exception("Unexpected task");
            if (tasks.size() == 0)
                onTaskPerformed();
        }
    }

    class ContactRequestTask extends TestTask {
        private IncomingCommandManager.IListener listener = new IncomingCommandManager.IListener() {
            @Override
            public void onCommandReceived(IncomingCommandManager.ReturnValue returnValue) {
                if (returnValue.command.equals(ContactRequest.COMMAND_NAME)) {
                    String contactId = ((ContactRequestHandler.ReturnValue)returnValue).contactId;
                    ContactPublic contactPublic = client2.getUserData().getContactStore().getContactList().get(
                            contactId);
                    Remote remote = contactPublic.getRemotes().getDefault();
                    System.out.println("Contact received: " + contactId + " user: " + remote.getUser() + " server: "
                            + remote.getServer());

                    client2.getIncomingCommandManager().removeListener(this);
                    onTaskPerformed();
                } else
                    fail("unexpected command");
            }

            @Override
            public void onException(Exception exception) {
                fail(exception.getMessage());
            }
        };

        @Override
        protected void perform(TestTask previousTask) throws Exception {
            client1.getUserData().getOutgoingCommandQueue().post(new ContactRequest(client1.getContext(),
                    client1.getUserData().getIdentityStore().getMyself(),
                    client1.getUserData().getRemoteList().getDefault()), USER_NAME_2, SERVER_URL);

            client2.getIncomingCommandManager().addListener(listener);
        }
    }

    class CreateAndSyncAccountTask extends TestTask {
        final private Client client;
        final private ClientStatus status;

        CreateAndSyncAccountTask(Client client, ClientStatus status) {
            this.client = client;
            this.status = status;
        }

        @Override
        protected void perform(TestTask previousTask) throws Exception {
            client.create(status.name, SERVER_URL, PASSWORD);
            client.commit();

            client.createAccount(status.name, PASSWORD, SERVER_URL, new SimpleObserver(new Runnable() {
                @Override
                public void run() {
                    try {
                        onAccountCreated(client, status);
                    } catch (Exception e) {
                        fail(e.getMessage());
                    }
                }
            }));
        }

        private void onAccountCreated(Client client, final ClientStatus status) throws Exception {
            System.out.print("Account Created");
            // watch
            client.startSyncing(new Task.IObserver<TaskUpdate, Void>() {
                @Override
                public void onProgress(TaskUpdate update) {
                    System.out.println(update.toString());
                    if (!status.firstSync && update.getTotalWork() > 0 && update.getProgress() == update.getTotalWork()) {
                        status.firstSync = true;
                        startCommandManagers();
                        onTaskPerformed();
                    }
                }

                @Override
                public void onResult(Void aVoid) {
                    System.out.println(status.name + ": sync ok");
                }

                @Override
                public void onException(Exception exception) {
                    fail();
                }
            });
        }

        private void startCommandManagers() {
            client.startCommandManagers(new Task.IObserver<TaskUpdate, Void>() {
                @Override
                public void onProgress(TaskUpdate update) {
                    System.out.println(status.name + ": " + update.toString());
                }

                @Override
                public void onResult(Void aVoid) {
                    System.out.println(status.name + ": Command sent");
                }

                @Override
                public void onException(Exception exception) {
                    fail();
                }
            });
        }
    }

    public void testClient() throws Exception {
        CreateAndSyncAccountTask createAccountTask1 = new CreateAndSyncAccountTask(client1, clientStatus1);
        CreateAndSyncAccountTask createAccountTask2 = new CreateAndSyncAccountTask(client2, clientStatus2);

        // merge both
        MergeTask firstSyncMergeTask = new MergeTask(createAccountTask1, createAccountTask2);

        // contact request
        ContactRequestTask contactRequestTask = new ContactRequestTask();
        firstSyncMergeTask.setNextTask(contactRequestTask);

        // finish
        FinishTask finishTask = new FinishTask();
        contactRequestTask.setNextTask(finishTask);

        // start it
        createAccountTask1.perform(null);
        createAccountTask2.perform(null);

        finishedSemaphore.acquire();
        Thread.sleep(1000);
        clientThread.quit(true);
    }
}
