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

    /**
     * Maintains the access tokens gained for different target users.
     *
     * The target user is identified by a string such as user@server.
     *
     * Must be thread safe to be accessed from a task job.
     */
    static class TokenManager {
        final private HashSet<String> rootAccess = new HashSet<>();
        final private Map<String, HashSet<String>> authMap = new HashMap<>();

        static private String makeKey(String serverUser, String server) {
            return serverUser + "@" + server;
        }

        public boolean hasRootAccess(String serverUser, String server) {
            synchronized (this) {
                return rootAccess.contains(makeKey(serverUser, server));
            }
        }

        public boolean addRootAccess(String serverUser, String server) {
            synchronized (this) {
                return rootAccess.add(makeKey(serverUser, server));
            }
        }

        public boolean removeRootAccess(String serverUser, String server) {
            synchronized (this) {
                return rootAccess.remove(makeKey(serverUser, server));
            }
        }

        public void addToken(String targetUser, String token) {
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(targetUser);
                if (tokenMap == null) {
                    tokenMap = new HashSet<>();
                    authMap.put(targetUser, tokenMap);
                }
                tokenMap.add(token);
            }
        }

        public boolean removeToken(String targetUser, String token) {
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(targetUser);
                if (tokenMap == null)
                    return false;
                return tokenMap.remove(token);
            }
        }

        public boolean hasToken(String targetUser, String token) {
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(targetUser);
                if (tokenMap == null)
                    return false;
                return tokenMap.contains(token);
            }
        }
    }

    //final private CookieStore cookieStore = new BasicCookieStore();
    final private TokenManager tokenManager = new TokenManager();
    private Task.IScheduler startScheduler = new Task.NewThreadScheduler();
    private Task.IScheduler observerScheduler = new Task.CurrentThreadScheduler();

    public ConnectionManager() {
        if (CookieHandler.getDefault() == null)
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
        JobTask<T> jobTask = new JobTask<>(tokenManager, job, connectionInfo, authInfo);
        return jobTask.setStartScheduler(startScheduler).setObserverScheduler(observerScheduler).start(observer);
    }

    static private class JobTask<T extends RemoteJob.Result> extends Task<Void, T>{
        final private TokenManager tokenManager;
        final private JsonRemoteJob<T> job;
        final private ConnectionInfo connectionInfo;
        final private AuthInfo authInfo;

        private IRemoteRequest remoteRequest;

        public JobTask(TokenManager tokenManager, final JsonRemoteJob<T> job, ConnectionInfo connectionInfo,
                       final AuthInfo authInfo) {
            super();

            this.tokenManager = tokenManager;
            this.job = job;
            this.connectionInfo = connectionInfo;
            this.authInfo = authInfo;

            setTaskFunction(new ITaskFunction<Void, T>() {
                @Override
                public void run(Task<Void, T> task) throws Exception {
                    JobTask.this.run(0);
                }

                @Override
                public void cancel() {
                    cancelJob();
                }
            });
        }

        final static private int MAX_RETRIES = 2;
        private void run(int retryCount) throws Exception {
            if (retryCount > MAX_RETRIES)
                throw new Exception("too many retries");
            IRemoteRequest remoteRequest = getRemoteRequest(connectionInfo);
            setCurrentRemoteRequest(remoteRequest);

            boolean hasAccess = hasAccess(authInfo);
            if (!hasAccess) {
                remoteRequest = getAuthRequest(remoteRequest, connectionInfo, authInfo);
                setCurrentRemoteRequest(remoteRequest);
            }

            T result = runJob(remoteRequest, job);
            if (result.status == Portal.Errors.ACCESS_DENIED) {
                if (authInfo.authType == AuthInfo.ROOT)
                    tokenManager.removeRootAccess(authInfo.serverUser, authInfo.server);
                if (authInfo.authType == AuthInfo.TOKEN)
                    tokenManager.removeToken(authInfo.serverUser, authInfo.token.getId());
                // if we had access try again
                if (hasAccess) {
                    run(retryCount + 1);
                    return;
                }
            }
            onResult(result);
        }

        private T runJob(final IRemoteRequest remoteRequest, final JsonRemoteJob<T> job) throws Exception {
            try {
                return JsonRemoteJob.run(job, remoteRequest);
            } finally {
                remoteRequest.close();
                setCurrentRemoteRequest(null);
            }
        }

        private void cancelJob() {
            synchronized (this) {
                if (remoteRequest != null)
                    remoteRequest.cancel();
            }
        }

        private void setCurrentRemoteRequest(IRemoteRequest remoteRequest) throws Exception {
            synchronized (this) {
                if (isCanceled()) {
                    this.remoteRequest = null;
                    throw new Exception("canceled");
                }

                this.remoteRequest = remoteRequest;
            }
        }

        private boolean hasAccess(AuthInfo authInfo) {
            if (authInfo.authType == AuthInfo.NONE)
                return true;
            if (authInfo.authType == AuthInfo.ROOT)
                return tokenManager.hasRootAccess(authInfo.serverUser, authInfo.server);
            if (authInfo.authType == AuthInfo.TOKEN)
                return tokenManager.hasToken(authInfo.serverUser, authInfo.token.getId());
            return false;
        }

        private IRemoteRequest getAuthRequest(final IRemoteRequest remoteRequest, final ConnectionInfo connectionInfo,
                                              final AuthInfo authInfo) throws Exception {
            RemoteJob.Result result;
            if (authInfo.authType == AuthInfo.ROOT) {
                result = runJob(remoteRequest, new RootLoginJob(authInfo.serverUser,
                        authInfo.password));
                tokenManager.addRootAccess(authInfo.serverUser, authInfo.server);
            } else if (authInfo.authType == AuthInfo.TOKEN) {
                result = runJob(remoteRequest, new AccessRequestJob(authInfo.serverUser,
                        authInfo.token));
                tokenManager.addToken(authInfo.serverUser, authInfo.token.getId());
            } else
                throw new Exception("unknown auth type");

            if (result.status == Portal.Errors.DONE)
                return getRemoteRequest(connectionInfo);

            throw new Exception(result.message);
        }

        private IRemoteRequest getRemoteRequest(ConnectionInfo connectionInfo) {
            return new HTMLRequest(connectionInfo.url);
        }
    }
}
