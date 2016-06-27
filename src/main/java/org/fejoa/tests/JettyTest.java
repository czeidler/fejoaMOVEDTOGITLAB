package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.remote.*;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.Task;
import org.fejoa.server.JettyServer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class JettyTest extends TestCase {
    final static String TEST_DIR = "jettyTest";
    final static String SERVER_TEST_DIR = TEST_DIR + "/Server";

    final List<String> cleanUpDirs = new ArrayList<String>();
    JettyServer server;
    ConnectionManager.ConnectionInfo connectionInfo;
    ConnectionManager.AuthInfo authInfo;
    Task.IObserver<Void, RemoteJob.Result> observer;
    ConnectionManager connectionManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        cleanUpDirs.add(TEST_DIR);

        server = new JettyServer(SERVER_TEST_DIR);
        server.start();

        connectionInfo = new ConnectionManager.ConnectionInfo("", "http://localhost:8080/");
        authInfo = new ConnectionManager.AuthInfo();
        observer = new Task.IObserver<Void, RemoteJob.Result>() {
            @Override
            public void onProgress(Void aVoid) {
                System.out.println("onProgress: ");
            }

            @Override
            public void onResult(RemoteJob.Result result) {
                System.out.println("onNext: " + result.message);
            }

            @Override
            public void onException(Exception exception) {
                fail(exception.getMessage());
            }
        };

        connectionManager = new ConnectionManager();
        connectionManager.setStartScheduler(new Task.CurrentThreadScheduler());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        Thread.sleep(1000);
        server.stop();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testSync2() throws Exception {
        String serverUser = "user1";
        String localGitDir = TEST_DIR + "/.git";
        String BRANCH = "testBranch";

        // push
        JGitInterface gitInterface = new JGitInterface();
        gitInterface.init(localGitDir, BRANCH, true);
        gitInterface.writeBytes("id", "testData".getBytes());
        gitInterface.commit();
        sync(connectionManager, gitInterface, serverUser);

        // do changes on the server
        JGitInterface serverGit = new JGitInterface();
        serverGit.init(SERVER_TEST_DIR + "/" + serverUser + "/.git", BRANCH, false);
        serverGit.writeBytes("hash/command", "testData".getBytes());
        serverGit.commit();
        sync(connectionManager, gitInterface, serverUser);

        serverGit.writeBytes("hash2/command", "testData".getBytes());
        serverGit.commit();

        // client delete
        gitInterface.remove("hash");
        gitInterface.commit();
        sync(connectionManager, gitInterface, serverUser);

        // test trivial merge
        serverGit.writeBytes("hash3/command", "testData".getBytes());
        serverGit.commit();
        sync(connectionManager, gitInterface, serverUser);

        // pull into empty git
        StorageLib.recursiveDeleteFile(new File(localGitDir));
        gitInterface = new JGitInterface();
        gitInterface.init(localGitDir, BRANCH, true);
        sync(connectionManager, gitInterface, serverUser);
    }

    public void testSync() throws Exception {
        String serverUser = "user1";
        String localGitDir = TEST_DIR + "/.git";
        String BRANCH = "testBranch";

        // push
        JGitInterface gitInterface = new JGitInterface();
        gitInterface.init(localGitDir, BRANCH, true);
        gitInterface.writeBytes("testFile", "testData".getBytes());
        gitInterface.commit();
        sync(connectionManager, gitInterface, serverUser);

        // do changes on the server
        JGitInterface serverGit = new JGitInterface();
        serverGit.init(SERVER_TEST_DIR + "/" + serverUser + "/.git", BRANCH, false);
        serverGit.writeBytes("testFileServer", "testDataServer".getBytes());
        serverGit.commit();

        // merge
        gitInterface.writeBytes("testFile2", "testDataClient2".getBytes());
        gitInterface.remove("testFile");
        gitInterface.commit();

        // sync
        sync(connectionManager, gitInterface, serverUser);

        // pull into empty git
        StorageLib.recursiveDeleteFile(new File(localGitDir));
        gitInterface = new JGitInterface();
        gitInterface.init(localGitDir, BRANCH, true);
        sync(connectionManager, gitInterface, serverUser);
    }

    private void sync(final ConnectionManager connectionManager, final JGitInterface gitInterface, final String serverUser) {
        connectionManager.submit(new GitPullJob(gitInterface.getRepository(), serverUser,
                gitInterface.getBranch()), connectionInfo, authInfo, new Task.IObserver<Void, GitPullJob.Result>() {
            @Override
            public void onProgress(Void aVoid) {
                observer.onProgress(aVoid);
            }

            @Override
            public void onResult(GitPullJob.Result result) {
                observer.onResult(result);
                try {
                    gitInterface.merge(result.pulledRev);
                    String tip = gitInterface.getTip();
                    if (tip.equals(result.pulledRev))
                        return;
                    connectionManager.submit(new GitPushJob(gitInterface.getRepository(), serverUser,
                            gitInterface.getBranch()), connectionInfo, authInfo, observer);
                } catch (IOException e) {
                    observer.onException(e);
                }
            }

            @Override
            public void onException(Exception exception) {
                observer.onException(exception);
            }
        });
    }

    public void testSimple() throws Exception {
        connectionManager.submit(new JsonPingJob(), connectionInfo, authInfo, observer);

        connectionManager.submit(new CreateAccountJob("userName", "password", "noUserDataBranch",
                CryptoSettings.getDefault().masterPassword), connectionInfo, authInfo, observer);
        Thread.sleep(1000);

        connectionManager.submit(new RootLoginJob("userName", "password"), connectionInfo, authInfo, observer);

        Thread.sleep(1000);
    }
}
