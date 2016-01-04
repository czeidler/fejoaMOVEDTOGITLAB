/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.fejoa.library2.AccessTokenContact;
import org.fejoa.server.Portal;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;
import java.util.HashSet;
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
        final static public int TOKEN = 2;

        final public int authType;
        final public String serverUser;
        final public String server;
        final public String password;
        final public AccessTokenContact token;

        public AuthInfo() {
            this.authType = NONE;
            this.token = null;
            this.server = null;
            this.serverUser = null;
            this.password = null;
        }

        public AuthInfo(String serverUser, String server, String password) {
            this.authType = ROOT;
            this.token = null;
            this.serverUser = serverUser;
            this.server = server;
            this.password = password;
        }

        public AuthInfo(String serverUser, AccessTokenContact token) {
            this.authType = TOKEN;
            this.token = token;
            this.serverUser = serverUser;
            this.server = null;
            this.password = null;
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
    static class TokenManager {
        final private HashSet<String> rootAccess = new HashSet<>();
        final private Map<String, HashSet<String>> authMap = new HashMap<>();

        static private String makeKey(String serverUser, String server) {
            return serverUser + "@" + server;
        }

        public boolean hasRootAccess(String serverUser, String server) {
            return rootAccess.contains(makeKey(serverUser, server));
        }

        public boolean addRootAccess(String serverUser, String server) {
            return rootAccess.add(makeKey(serverUser, server));
        }

        public boolean removeRootAccess(String serverUser, String server) {
            return rootAccess.remove(makeKey(serverUser, server));
        }

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
    final private TokenManager tokenManager = new TokenManager();
    private Task.IScheduler startScheduler = new Task.NewThreadScheduler();
    private Task.IScheduler observerScheduler = new Task.CurrentThreadScheduler();

    public ConnectionManager() {
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
        IRemoteRequest remoteRequest = getRemoteRequest(connectionInfo);
        if (remoteRequest == null)
            return null;
        remoteRequest = getAuthRequest(remoteRequest, connectionInfo, authInfo, observer);
        if (remoteRequest == null)
            return null;
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

    private <T extends RemoteJob.Result> IRemoteRequest getAuthRequest(final IRemoteRequest remoteRequest,
                                                                       final ConnectionInfo connectionInfo,
                                                                       final AuthInfo authInfo,
                                                                       final Task.IObserver<Void, T> observer) {
        if (authInfo.authType == AuthInfo.NONE)
            return remoteRequest;
        final IRemoteRequest[] returnValue = new IRemoteRequest[1];
        Task.IObserver<Void, RemoteJob.Result> authJobObserver = new Task.IObserver<Void, RemoteJob.Result>() {
            @Override
            public void onProgress(Void v) {

            }

            @Override
            public void onResult(RemoteJob.Result result) {
                if (result.status == Portal.Errors.DONE) {
                    returnValue[0] = getRemoteRequest(connectionInfo);
                } else {
                    observer.onException(new Exception(result.message));
                }
            }

            @Override
            public void onException(Exception exception) {
                observer.onException(exception);
            }
        };

        if (authInfo.authType == AuthInfo.ROOT) {
            if (tokenManager.hasRootAccess(authInfo.serverUser, authInfo.server))
                return remoteRequest;
            runJob(remoteRequest, new RootLoginJob(authInfo.serverUser, authInfo.password))
                    .setStartScheduler(new Task.CurrentThreadScheduler())
                    .setObserverScheduler(new Task.CurrentThreadScheduler())
                    .start(authJobObserver);
            if (returnValue[0] != null)
                tokenManager.addRootAccess(authInfo.serverUser, authInfo.server);
        } else if (authInfo.authType == AuthInfo.TOKEN) {
            if (tokenManager.hasToken(authInfo.serverUser, authInfo.token.getId()))
                return remoteRequest;
            runJob(remoteRequest, new AccessRequestJob(authInfo.serverUser, authInfo.token))
                    .setStartScheduler(new Task.CurrentThreadScheduler())
                    .setObserverScheduler(new Task.CurrentThreadScheduler())
                    .start(authJobObserver);
            if (returnValue[0] != null)
                tokenManager.addToken(authInfo.serverUser, authInfo.token.getId());
        }

        return returnValue[0];
    }

    private IRemoteRequest getRemoteRequest(ConnectionInfo connectionInfo) {
        return new HTMLRequest(connectionInfo.url);
    }
}
