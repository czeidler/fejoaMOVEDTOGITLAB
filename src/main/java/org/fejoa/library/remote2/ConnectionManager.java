/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.fejoa.library.ContactPrivate;
import org.json.JSONObject;
import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscription;
import rx.concurrency.Schedulers;
import rx.util.functions.Func1;

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
    private Scheduler observerScheduler = Schedulers.immediate();
    final private TokenManager tokenManager = new TokenManager();

    public ConnectionManager(ContactPrivate myself) {
        this.myself = myself;
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    public void setObserverScheduler(Scheduler observerScheduler) {
        this.observerScheduler = observerScheduler;
    }

    public Subscription submit(final JsonRemoteJob job, ConnectionInfo connectionInfo, final AuthInfo authInfo,
                       Observer<RemoteJob.Result> observer) {
        return getRemoteRequest(connectionInfo).mapMany(new Func1<IRemoteRequest, Observable<IRemoteRequest>>() {
            @Override
            public Observable<IRemoteRequest> call(IRemoteRequest remoteRequest) {
                return getAuthRequest(remoteRequest, authInfo);
            }
        }).mapMany(new Func1<IRemoteRequest, Observable<RemoteJob.Result>>() {
            @Override
            public Observable<RemoteJob.Result> call(IRemoteRequest remoteRequest) {
                return runJob(remoteRequest, job);
            }
        }).subscribeOn(Schedulers.threadPoolForIO()).observeOn(observerScheduler).subscribe(observer);
    }

    private Observable<RemoteJob.Result> runJob(final IRemoteRequest remoteRequest, final JsonRemoteJob job) {
        return Observable.create(new Observable.OnSubscribeFunc<RemoteJob.Result>() {
            @Override
            public Subscription onSubscribe(final Observer<? super RemoteJob.Result> observer) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            RemoteJob.Result result = JsonRemoteJob.run(job, remoteRequest, generalErrorHandler);
                            observer.onNext(result);
                            observer.onCompleted();
                        } catch (Exception e) {
                            e.printStackTrace();
                            observer.onError(e);
                        } finally {
                            remoteRequest.close();
                        }
                    }
                }).start();

                return new Subscription() {
                    @Override
                    public void unsubscribe() {
                        remoteRequest.cancel();
                    }
                };
            }
        });
    }

    private Observable<IRemoteRequest> getAuthRequest(final IRemoteRequest remoteRequest, final AuthInfo authInfo) {
        return Observable.create(new Observable.OnSubscribeFunc<IRemoteRequest>() {
            @Override
            public Subscription onSubscribe(Observer<? super IRemoteRequest> observer) {
                observer.onNext(remoteRequest);
                observer.onCompleted();
                return null;
            }
        });
    }

    private Observable<IRemoteRequest> getRemoteRequest(final ConnectionInfo connectionInfo) {
        return Observable.create(new Observable.OnSubscribeFunc<IRemoteRequest>() {
            @Override
            public Subscription onSubscribe(Observer<? super IRemoteRequest> observer) {
                observer.onNext(new HTMLRequest(connectionInfo.url));
                observer.onCompleted();
                return null;
            }
        });
    }
}
