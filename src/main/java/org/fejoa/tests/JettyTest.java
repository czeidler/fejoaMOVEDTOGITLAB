package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library2.remote.*;
import org.fejoa.library.support.StorageLib;
import org.fejoa.server.JettyServer;
import rx.Observer;
import rx.concurrency.Schedulers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class JettyTest extends TestCase {
    final static String TEST_DIR = "jettyTest";
    final static String SERVER_TEST_DIR = TEST_DIR + "/Server";

    final List<String> cleanUpDirs = new ArrayList<String>();
    JettyServer server;
    ConnectionManager.ConnectionInfo connectionInfo;
    ConnectionManager.AuthInfo authInfo;
    Observer<RemoteJob.Result> observer;
    ConnectionManager connectionManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        cleanUpDirs.add(TEST_DIR);

        server = new JettyServer(SERVER_TEST_DIR);
        server.start();

        connectionInfo = new ConnectionManager.ConnectionInfo("", "http://localhost:8080/");
        authInfo = new ConnectionManager.AuthInfo(ConnectionManager.AuthInfo.NONE, null);
        observer = new Observer<RemoteJob.Result>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("onError: " + throwable.getMessage());
            }

            @Override
            public void onNext(RemoteJob.Result result) {
                System.out.println("onNext: " + result.message);
            }
        };

        connectionManager = new ConnectionManager(null);
        connectionManager.setObserverScheduler(Schedulers.immediate());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        Thread.sleep(1000);
        server.stop();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
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
        connectionManager.submit(new GitSyncJob(gitInterface.getRepository(), serverUser, gitInterface.getBranch()),
                connectionInfo, authInfo, observer);

        Thread.sleep(1000);

        // do changes on the server
        JGitInterface serverGit = new JGitInterface();
        serverGit.init(SERVER_TEST_DIR + "/" + serverUser + "/.git", BRANCH, false);
        serverGit.writeBytes("testFileServer", "testDataServer".getBytes());
        serverGit.commit();

        // merge
        gitInterface.writeBytes("testFile2", "testDataClient2".getBytes());
        gitInterface.commit();

        // sync
        connectionManager.submit(new GitSyncJob(gitInterface.getRepository(), serverUser,
                gitInterface.getBranch()), connectionInfo, authInfo, observer);
        Thread.sleep(2000);

        // pull into empty git
        StorageLib.recursiveDeleteFile(new File(localGitDir));
        gitInterface = new JGitInterface();
        gitInterface.init(localGitDir, BRANCH, true);
        connectionManager.submit(new GitSyncJob(gitInterface.getRepository(), serverUser,
                gitInterface.getBranch()), connectionInfo, authInfo, observer);
        Thread.sleep(2000);
    }

    public void testSimple() throws Exception {
        connectionManager.submit(new JsonPingJob(), connectionInfo, authInfo, observer);

        connectionManager.submit(new CreateAccountJob("userName", "password",
                CryptoSettings.getDefault().masterPassword), connectionInfo, authInfo, observer);
        Thread.sleep(1000);

        connectionManager.submit(new RootLoginJob("userName", "password"), connectionInfo, authInfo, observer);

        Thread.sleep(1000);
    }
}
