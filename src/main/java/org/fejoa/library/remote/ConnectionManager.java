/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.INotifications;
import org.fejoa.library.support.IFejoaSchedulers;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;

import java.io.IOException;
import java.util.*;


class SharedConnection {
    final private ConnectionManager connectionManager;
    final private String server;
    final private CookieStore cookieStore;
    private Map<ContactPrivate, RoleManager> contactRoles = new HashMap<>();
    private Object initLock = new Object();
    private boolean sessionInitialized = false;

    public SharedConnection(ConnectionManager connectionManager, String server, CookieStore cookieStore) {
        this.connectionManager = connectionManager;
        this.server = server;
        this.cookieStore = cookieStore;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public RoleManager getRoleManager(ContactPrivate myself) {
        if (!contactRoles.containsKey(myself)) {
            RoleManager roleManager = new RoleManager(this);
            contactRoles.put(myself, roleManager);
            return roleManager;
        }
        return contactRoles.get(myself);
    }

    Observable<IRemoteRequest> getRemoteRequest() {
        return Observable.create(new Observable.OnSubscribeFunc<IRemoteRequest>() {
            @Override
            public Subscription onSubscribe(Observer<? super IRemoteRequest> observer) {
                String fullAddress = "http://" + server + "/php_server/portal.php";
                HTMLRequest remoteRequest = new HTMLRequest(fullAddress, cookieStore);

                synchronized (initLock) {
                    // Create a php session id (when using php). If there are concurrent requests without a session id
                    // php creates a new session id for each request, this could lead to hick ups in the client. To
                    // avoid this we first do a blocking ping call to get a session id.
                    if (!sessionInitialized) {
                        try {
                            remoteRequest.send("<phpPing/>".getBytes());
                            sessionInitialized = true;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                observer.onNext(remoteRequest);
                observer.onCompleted();
                return Subscriptions.empty();
            }
        });
    }

    public void shutdown(boolean force) {
        for (RoleManager roleManager : contactRoles.values())
            roleManager.shutdown(force);
        contactRoles.clear();
    }
}

class RoleManager {
    final private SharedConnection sharedConnection;
    final private RequestQueue requestQueue;
    final private ServerWatcher serverWatcher;
    final private List<String> roles = Collections.synchronizedList(new ArrayList<String>());

    public RoleManager(SharedConnection sharedConnection) {
        this.sharedConnection = sharedConnection;
        requestQueue = new RequestQueue(null, sharedConnection.getConnectionManager().getFejoaSchedulers());
        serverWatcher = new ServerWatcher(sharedConnection.getConnectionManager());
    }

    public void setListener(ServerWatcher.IListener listener) {
        serverWatcher.setListener(listener);
    }

    public void startWatching(List<RemoteStorageLink> links) {
        serverWatcher.setLinks(links);
        requestQueue.setIdleJob(serverWatcher);
    }

    private boolean hasRole(String role) {
        synchronized (roles) {
            return roles.contains(role);
        }
    }

    private void addRole(String role) {
        synchronized (roles) {
            roles.add(role);
        }
    }

    public Observable<IRemoteRequest> getRemoteRequest() {
        return sharedConnection.getRemoteRequest();
    }

    public Observable<IRemoteRequest> getPreparedRemoteRequest(final ConnectionInfo info) {
        return Observable.create(new Observable.OnSubscribeFunc<IRemoteRequest>() {
            @Override
            public Subscription onSubscribe(Observer<? super IRemoteRequest> observer) {
                Observable<IRemoteRequest> observable = sharedConnection.getRemoteRequest().mapMany(
                        new Func1<IRemoteRequest, Observable<IRemoteRequest>>() {
                            @Override
                            public Observable<IRemoteRequest> call(IRemoteRequest remoteRequest) {
                                return login(remoteRequest, info);
                            }
                        });
                return observable.subscribe(observer);
            }
        });
    }

    public <T> Observable<T> queueJob(Observable<T> observable) {
        return requestQueue.queue(observable);
    }

    private Observable<IRemoteRequest> login(final IRemoteRequest remoteRequest, final ConnectionInfo info) {
        final String role = info.myself.getUid() + ":" + info.serverUser;
        if (hasRole(role))
            return Observable.just(remoteRequest);

        return Observable.create(new Observable.OnSubscribeFunc<IRemoteRequest>() {
            @Override
            public Subscription onSubscribe(final Observer<? super IRemoteRequest> observer) {
                if (hasRole(role)) {
                    observer.onNext(remoteRequest);
                    observer.onCompleted();
                    return Subscriptions.empty();
                }
                SignatureAuthentication authentication = new SignatureAuthentication(info);
                authentication.auth(remoteRequest).subscribe(new Observer<Boolean>() {
                    @Override
                    public void onCompleted() {
                        observer.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        observer.onError(e);
                    }

                    @Override
                    public void onNext(Boolean signedIn) {
                        if (signedIn) {
                            addRole(role);
                            observer.onNext(remoteRequest);
                        } else
                            observer.onError(new Exception("failed to log in"));
                    }
                });
                return Subscriptions.empty();
            }
        });
    }

    public void shutdown(boolean force) {
        requestQueue.shutdown(force);
        roles.clear();
    }
}

public class ConnectionManager {
    final IFejoaSchedulers fejoaSchedulers;
    final CookieStore cookieStore = new BasicCookieStore();
    final Map<String, SharedConnection> servers = new HashMap<>();
    INotifications notifications = null;

    public ConnectionManager(IFejoaSchedulers fejoaSchedulers) {
        this.fejoaSchedulers = fejoaSchedulers;
    }

    public IFejoaSchedulers getFejoaSchedulers() {
        return fejoaSchedulers;
    }

    public void setNotifications(INotifications notifications) {
        this.notifications = notifications;
    }

    private SharedConnection getContactRoles(String server) {
        SharedConnection sharedConnection;
        if (!servers.containsKey(server)) {
            sharedConnection = new SharedConnection(this, server, cookieStore);
            servers.put(server, sharedConnection);
            return sharedConnection;
        }
        return servers.get(server);
    }

    private RoleManager getRoleManager(ConnectionInfo info) {
        SharedConnection sharedConnection = getContactRoles(info.server);
        return sharedConnection.getRoleManager(info.myself);
    }

    public Observable<IRemoteRequest> getRemoteRequest(ConnectionInfo info) {
        return getRoleManager(info).getRemoteRequest();
    }

    public Observable<IRemoteRequest> getPreparedRemoteRequest(ConnectionInfo info) {
        return getRoleManager(info).getPreparedRemoteRequest(info);
    }

    public <T> Observable<T> queueJob(ConnectionInfo info, Observable<T> observable) {
        return getRoleManager(info).queueJob(observable);
    }

    public RemoteConnection getConnection(ConnectionInfo info) {
        return new RemoteConnection(this, info);
    }

    /**
     * Start watching links that have the same ConnectionInfo.
     */
    public void startWatching(List<RemoteStorageLink> links, ServerWatcher.IListener listener) {
        if (links.size() == 0)
            return;
        ConnectionInfo info = links.get(0).getConnectionInfo();
        getRoleManager(info).startWatching(links);
        getRoleManager(info).setListener(listener);
    }

    public void shutdown(boolean force) {
        for (SharedConnection connection : servers.values())
            connection.shutdown(force);
        servers.clear();
    }
}

