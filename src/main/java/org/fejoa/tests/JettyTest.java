package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.remote2.ConnectionManager;
import org.fejoa.library.remote2.GitPushJob;
import org.fejoa.library.remote2.JsonPingJob;
import org.fejoa.library.remote2.RemoteJob;
import org.fejoa.server.JettyServer;
import rx.Observer;
import rx.concurrency.Schedulers;


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

        connectionManager.submit(new GitPushJob(), connectionInfo, authInfo, observer);

        Thread.sleep(10000);
        server.stop();
    }
}
