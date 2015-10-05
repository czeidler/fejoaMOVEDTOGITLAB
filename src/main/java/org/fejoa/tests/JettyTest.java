package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.remote2.*;
import org.fejoa.library.support.StorageLib;
import org.fejoa.server.JettyServer;
import rx.Observer;
import rx.concurrency.Schedulers;

import java.io.File;


public class JettyTest extends TestCase {
    public void testSimple() throws Exception {
        JettyServer server = new JettyServer();

        server.start();

        ConnectionManager.ConnectionInfo connectionInfo = new ConnectionManager.ConnectionInfo("", "http://localhost:8080/");
        ConnectionManager.AuthInfo authInfo = new ConnectionManager.AuthInfo(ConnectionManager.AuthInfo.NONE, null);
        Observer<RemoteJob.Result> observer = new Observer<RemoteJob.Result>() {
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

        ConnectionManager connectionManager = new ConnectionManager(null);
        connectionManager.setObserverScheduler(Schedulers.immediate());

        connectionManager.submit(new JsonPingJob(), connectionInfo, authInfo, observer);

        // push
        /*JGitInterface gitInterface = new JGitInterface();
        gitInterface.init(".gitTestClient", "testBranch", true);
        gitInterface.writeBytes("testFile", "testData".getBytes());
        gitInterface.commit();
        connectionManager.submit(new GitPushJob(gitInterface.getRepository(), gitInterface.getBranch()),
                connectionInfo, authInfo, observer);
*/

        // pull
        /*
        JGitInterface gitInterface = new JGitInterface();
        gitInterface.init(".gitTest", "testBranch", true);
        gitInterface.writeBytes("testFileServer", "testDataServer".getBytes());
        gitInterface.commit();

        JGitInterface gitClientInterface = new JGitInterface();
        gitClientInterface.init(".gitTestClient", "testBranch", true);
        connectionManager.submit(new GitPullJob(gitClientInterface.getRepository(), gitClientInterface.getBranch()),
                connectionInfo, authInfo, observer);
*/
        connectionManager.submit(new CreateAccountJob("userName", "password",
                CryptoSettings.getDefault().masterPassword), connectionInfo, authInfo, observer);
        Thread.sleep(1000);

        connectionManager.submit(new RootLoginJob("userName", "password"), connectionInfo, authInfo, observer);

        Thread.sleep(10000);
        server.stop();

        StorageLib.recursiveDeleteFile(new File(".gitTestClient"));
        StorageLib.recursiveDeleteFile(new File(".gitTest"));
    }
}
