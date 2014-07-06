/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.support.ProtocolOutStream;
import rx.Observable;
import rx.Observer;
import rx.util.functions.Func1;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;


public class RemoteConnection {
    public IRemoteRequest remoteRequest = null;
    final private Map<String, AuthenticationState> roleMap = new HashMap<>();

    private class AuthenticationState {
        public boolean connected = false;
        public IAuthenticationRequest authenticationRequest = null;

        public AuthenticationState(IAuthenticationRequest request) {
            this.authenticationRequest = request;
        }
    }

    public RemoteConnection(IRemoteRequest remoteRequest) {
        this.remoteRequest = remoteRequest;
    }

    public Observable<byte[]> send(byte data[]) {
        return remoteRequest.send(data);
    }

    public IRemoteRequest getRemoteRequest() {
        return remoteRequest;
    }

    static public byte[] send(final IRemoteRequest remoteRequest, byte out[]) throws IOException {
        final byte[][] reply = new byte[1][1];
        final Throwable[] error = {null};
        Observable<byte[]> remote = remoteRequest.send(out);
        remote.subscribe(new Observer<byte[]>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable throwable) {
                error[0] = throwable;
            }

            @Override
            public void onNext(byte[] bytes) {
                reply[0] = bytes;
            }
        });

        if (error[0] != null)
            throw new IOException(error[0].getMessage());

        return reply[0];
    }

    static public byte[] send(final IRemoteRequest remoteRequest, ProtocolOutStream outStream)
            throws TransformerException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out);
        outStream.write(writer);

        return RemoteConnection.send(remoteRequest, out.toByteArray());
    }

    static public boolean authenticate(IRemoteRequest remoteRequest, IAuthenticationRequest authenticationRequest) {
        Observable<Boolean> remote = authenticationRequest.send(remoteRequest);
        final boolean[] status = {false};
        remote.subscribe(new Observer<Boolean>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable throwable) {
                status[0] = false;
            }

            @Override
            public void onNext(Boolean result) {
                status[0] = result;
            }
        });
        return status[0];
    }

    private AuthenticationState addAuthenticationState(String role, IAuthenticationRequest request) {
        AuthenticationState state = new AuthenticationState(request);
        roleMap.put(role, state);
        return state;
    }

    public Observable<Boolean> requestAccountUser(String loginUser, String serverUser, ContactPrivate user) {
        String role = loginUser + ":" + serverUser;
        AuthenticationState state;
        if (!roleMap.containsKey(role))
            state = addAuthenticationState(role, new SignatureAuthentication(loginUser, serverUser, user));
        else
            state = roleMap.get(role);

        return requestRole(state);
    }

    private Observable<Boolean> requestRole(final AuthenticationState state) {
        if (state.connected)
            return Observable.just(true);

        return state.authenticationRequest.send(remoteRequest).map(new Func1<Boolean, Boolean>() {
            @Override
            public Boolean call(Boolean connected) {
                state.connected = connected;
                return connected;
            }
        });
    }
}
