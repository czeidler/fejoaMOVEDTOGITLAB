/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.fejoa.library.ContactPrivate;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;


public class ConnectionManager {
    static public class ConnectionInfo {
        final public String user;
        final public String url;

        public ConnectionInfo(String user, String url) {
            this.user = user;
            this.url = url;
        }
    }

    static public class AuthInfo {
        final static public int NONE = 0;
        final static public int ROOT = 1;
        final static public int TOKENS = 2;

        final public int authType;
        final public List<String> tokens;

        public AuthInfo(int authType, List<String> tokens) {
            this.authType = authType;
            this.tokens = tokens;
        }
    }

    private JsonRemoteJob.IErrorCallback generalErrorHandler = new JsonRemoteJob.IErrorCallback() {
        @Override
        public void onError(JSONObject returnValue, InputStream binaryData) {

        }
    };

    /**
     * Maintains the access tokens gained for different target users.
     *
     * The target user is identified by a string such as user@server.
     */
    class TokenManager {
        final private Map<String, HashSet<String>> authMap = new HashMap<>();

        public void addToken(String targetUser, String token) {
            HashSet<String> tokenMap = authMap.get(targetUser);
            if (tokenMap == null) {
                tokenMap = new HashSet<>();
                authMap.put(targetUser, tokenMap);
            }
            tokenMap.add(token);
        }

        public boolean removeToken(String targetUser, String token) {
            HashSet<String> tokenMap = authMap.get(targetUser);
            if (tokenMap == null)
                return false;
            return tokenMap.remove(token);
        }

        public boolean hasToken(String targetUser, String token) {
            HashSet<String> tokenMap = authMap.get(targetUser);
            if (tokenMap == null)
                return false;
            return tokenMap.contains(token);
        }
    }

    //final private CookieStore cookieStore = new BasicCookieStore();
    final private ContactPrivate myself;
    final private TokenManager tokenManager = new TokenManager();
    private Task.IScheduler startScheduler = new Task.NewThreadScheduler();
    private Task.IScheduler observerScheduler = new Task.CurrentThreadScheduler();

    public ConnectionManager(ContactPrivate myself) {
        this.myself = myself;
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    public void setStartScheduler(Task.IScheduler startScheduler) {
        this.startScheduler = startScheduler;
    }

    public void setObserverScheduler(Task.IScheduler scheduler) {
        this.observerScheduler = scheduler;
    }

    public <T extends RemoteJob.Result> Task.ICancelFunction submit(final JsonRemoteJob<T> job,
                                                                    ConnectionInfo connectionInfo,
                                                                    final AuthInfo authInfo,
                                                                    final Task.IObserver<Void, T> observer) {
        IRemoteRequest remoteRequest = getAuthRequest(getRemoteRequest(connectionInfo), authInfo);
        return runJob(remoteRequest, job).setStartScheduler(startScheduler).setObserverScheduler(observerScheduler)
                .start(observer);
    }

    private <T extends RemoteJob.Result> Task<Void, T> runJob(final IRemoteRequest remoteRequest,
                                                              final JsonRemoteJob<T> job) {

        return new Task<>(new Task.ITaskFunction<Void, T>() {
            @Override
            public void run(Task<Void, T> task) throws Exception {
                try {
                    T result = JsonRemoteJob.run(job, remoteRequest, generalErrorHandler);
                    task.onResult(result);
                } catch (Exception e) {
                    e.printStackTrace();
                    task.onException(e);
                } finally {
                    remoteRequest.close();
                }
            }

            @Override
            public void cancel() {
                remoteRequest.cancel();
            }
        });
    }

    private IRemoteRequest getAuthRequest(final IRemoteRequest remoteRequest, final AuthInfo authInfo) {
        return remoteRequest;
    }

    private IRemoteRequest getRemoteRequest(ConnectionInfo connectionInfo) {
        return new HTMLRequest(connectionInfo.url);
    }
}
