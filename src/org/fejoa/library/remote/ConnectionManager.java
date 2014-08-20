/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.ContactPrivate;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import java.util.*;


class RoleManager {
    final private IRemoteRequest remoteRequest;
    final private RequestQueue requestQueue;
    final private List<String> roles = Collections.synchronizedList(new ArrayList<String>());

    public RoleManager(IRemoteRequest remoteRequest) {
        this.remoteRequest = remoteRequest;
        requestQueue = new RequestQueue(null);
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

    public Observable<byte[]> sendQueued(byte data[], ConnectionInfo info) {
        return requestQueue.queue(send(data, info));
    }

    private Observable<byte[]> send(final byte data[], final ConnectionInfo info) {
        return Observable.create(new Observable.OnSubscribeFunc<byte[]>() {
            @Override
            public Subscription onSubscribe(final Observer<? super byte[]> observer) {
                boolean loggedIn = loginSynced(info.myself, info.serverUser);
                if (!loggedIn) {
                    observer.onError(new Exception("Failed to login!"));
                    return null;
                }

                return remoteRequest.send(data).subscribe(observer);
            }
        });
    }

    public boolean loginSynced(ContactPrivate loginUser, String serverUser) {
        final boolean[] result = new boolean[1];
        login(loginUser, serverUser).subscribe(new Observer<Boolean>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                result[0] = false;
            }

            @Override
            public void onNext(Boolean args) {
                result[0] = args;
            }
        });
        return result[0];
    }

    public Observable<Boolean> login(final ContactPrivate loginUser, final String serverUser) {
        final String role = loginUser.getUid() + ":" + serverUser;
        if (hasRole(role))
            return Observable.just(true);

        return Observable.create(new Observable.OnSubscribeFunc<Boolean>() {
            @Override
            public Subscription onSubscribe(final Observer<? super Boolean> observer) {
                if (hasRole(role)) {
                    observer.onNext(true);
                    observer.onCompleted();
                    return Subscriptions.empty();
                }
                SignatureAuthentication authentication = new SignatureAuthentication(loginUser, serverUser);
                authentication.send(remoteRequest).subscribe(new Observer<Boolean>() {
                    @Override
                    public void onCompleted() {
                        observer.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        observer.onError(e);
                    }

                    @Override
                    public void onNext(Boolean signed) {
                        if (signed)
                            addRole(role);
                        observer.onNext(signed);
                    }
                });
                return Subscriptions.empty();
            }
        });
    }
}

class ConnectionInfo {
    public String server;
    public String serverUser;
    public ContactPrivate myself;
}

class RemoteConnection {
    final ConnectionManager manager;
    final ConnectionInfo info;

    public RemoteConnection(ConnectionManager manager, ConnectionInfo info) {
        this.manager = manager;
        this.info = info;
    }

    public Observable<byte[]> send(byte[] data) {
        return manager.send(data, info);
    }
}

public class ConnectionManager {
    final private Map<String, RoleManager> servers = new HashMap<>();

    public Observable<byte[]> send(byte data[], ConnectionInfo info) {
        RoleManager roleManager;
        if (!servers.containsKey(info.server)) {
            String fullAddress = "http://" + info.server + "/php_server/portal.php";
            roleManager = new RoleManager(new HTMLRequest(fullAddress));
            servers.put(info.server, roleManager);
        } else
            roleManager = servers.get(info.server);

        return roleManager.sendQueued(data, info);
    }

    public RemoteConnection getConnection(ConnectionInfo info) {
        return new RemoteConnection(this, info);
    }
}

