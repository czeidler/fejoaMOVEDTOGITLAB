/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.eclipse.jgit.util.Base64;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.support.InStanzaHandler;
import org.fejoa.library.support.IqInStanzaHandler;
import org.fejoa.library.support.ProtocolInStream;
import org.fejoa.library.support.ProtocolOutStream;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Action1;
import rx.util.functions.Func1;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;


public class SignatureAuthentication implements IAuthenticationRequest {
    final String AUTH_STANZA = "auth";
    final String AUTH_SIGNED_STANZA = "auth_signed";

    final private String loginUser;
    final private String serverUser;
    final ContactPrivate user;

    private List<String> roles;

    public SignatureAuthentication(String loginUser, String serverUser, ContactPrivate user) {
        this.loginUser = loginUser;
        this.serverUser = serverUser;
        this.user = user;
    }

    @Override
    public Observable<Boolean> send(final IRemoteRequest remoteRequest) {

            return Observable.create(new Observable.OnSubscribeFunc<Boolean>() {
                @Override
                public Subscription onSubscribe(final Observer<? super Boolean> observer) {
                    String signatureToken = null;
                    try {
                        signatureToken = sendLoginRequest(remoteRequest);
                        boolean result = signIn(signatureToken, remoteRequest);
                        observer.onNext(result);
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                    observer.onCompleted();

                    return Subscriptions.empty();
                }
            });
    }

    private String sendLoginRequest(IRemoteRequest remoteRequest) throws ParserConfigurationException,
            TransformerException, IOException {
        ProtocolOutStream outStream = new ProtocolOutStream();
        Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_GET);

        Element authStanza = outStream.createElement(AUTH_STANZA);
        authStanza.setAttribute("type", "signature");
        authStanza.setAttribute("loginUser", loginUser);
        authStanza.setAttribute("serverUser", serverUser);
        iqStanza.appendChild(authStanza);

        outStream.addElement(iqStanza);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out);
        outStream.write(writer);

        byte reply[] = send(remoteRequest, out.toByteArray());

        IqInStanzaHandler iqHandler = new IqInStanzaHandler(ProtocolOutStream.IQ_RESULT);
        UserAuthHandler userAuthHandler = new UserAuthHandler();
        iqHandler.addChildHandler(userAuthHandler);

        ProtocolInStream inStream = new ProtocolInStream(reply);
        inStream.addHandler(iqHandler);
        inStream.parse();

        if (!userAuthHandler.hasBeenHandled())
            throw new IOException();

        if (userAuthHandler.status.equals("i_dont_know_you"))
            throw new IOException("no contact");
        if (!userAuthHandler.status.equals("sign_this_token"))
            throw new IOException("no sign token");

        return userAuthHandler.signToken;
    }

    private byte[] send(final IRemoteRequest remoteRequest, byte out[]) throws IOException {
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

    class UserAuthHandler extends InStanzaHandler {
        public String status = "";
        public String signToken = "";

        public UserAuthHandler() {
            super(AUTH_STANZA, false);
        }

        @Override
        public boolean handleStanza(Attributes attributes) {
            if (attributes.getIndex("status") < 0)
                return false;
            status = attributes.getValue("status");

            if (attributes.getIndex("sign_token") >= 0)
                signToken = attributes.getValue("sign_token");
            return true;
        }
    };

    class UserAuthResultHandler extends InStanzaHandler {
        public UserAuthResultHandler() {
            super(AUTH_SIGNED_STANZA, false);
        }

        public boolean handleStanza(Attributes attributes) {
            return true;
        }
    };

    class AuthResultRoleHandler extends InStanzaHandler {
        public List<String> roles = new ArrayList<>();

        public AuthResultRoleHandler() {
            super("role", true);
        }

        @Override
        public boolean handleStanza(Attributes attributes) {
            return true;
        }

        @Override
        public boolean handleText(String text) {
            roles.add(text);
            return true;
        }
    };

    private boolean signIn(String signatureToken, final IRemoteRequest remoteRequest) throws Exception {
        byte signature[] = user.sign(user.getMainKeyId(), signatureToken.getBytes());

        ProtocolOutStream outStream = new ProtocolOutStream();
        Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_SET);
        outStream.addElement(iqStanza);
        Element authStanza =  outStream.createElement(AUTH_SIGNED_STANZA);
        authStanza.setAttribute("signature", Base64.encodeBytes(signature));
        authStanza.setAttribute("serverUser", serverUser);
        authStanza.setAttribute("loginUser", loginUser);
        iqStanza.appendChild(authStanza);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out);
        outStream.write(writer);

        byte reply[] = send(remoteRequest, out.toByteArray());

        IqInStanzaHandler iqHandler = new IqInStanzaHandler(ProtocolOutStream.IQ_RESULT);
        UserAuthResultHandler userAuthResutlHandler = new UserAuthResultHandler();
        AuthResultRoleHandler roleHander = new AuthResultRoleHandler();
        userAuthResutlHandler.addChildHandler(roleHander);
        iqHandler.addChildHandler(userAuthResutlHandler);

        ProtocolInStream inStream = new ProtocolInStream(reply);
        inStream.addHandler(iqHandler);
        inStream.parse();

        if (!userAuthResutlHandler.hasBeenHandled())
            throw new Exception();

        roles = roleHander.roles;
        if (roles.size() == 0)
            return false;

        return true;
    }
}
