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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class SignatureAuthentication implements IAuthenticationRequest {
    final String AUTH_STANZA = "auth";
    final String AUTH_SIGNED_STANZA = "auth_signed";

    final private String serverUser;
    final ContactPrivate user;

    private List<String> roles;

    public SignatureAuthentication(ContactPrivate loginUser, String serverUser) {
        this.serverUser = serverUser;
        this.user = loginUser;
    }

    public Observable<Boolean> auth(final IRemoteRequest remoteRequest) {
        final LoginRequest loginRequest = new LoginRequest();
        final SignIn signIn = new SignIn(loginRequest);

        return Observable.create(new Observable.OnSubscribeFunc<Boolean>() {
            @Override
            public Subscription onSubscribe(Observer<? super Boolean> observer) {
                try {
                    byte[] request = loginRequest.getRequest();
                    byte[] reply = RemoteRequestHelper.sendSync(remoteRequest, request);
                    if (reply == null)
                        throw new IOException("network error");
                    if (!loginRequest.handleResponse(reply).done)
                        throw new IOException("bad response");
                    request = signIn.getRequest();
                    reply = RemoteRequestHelper.sendSync(remoteRequest, request);
                    if (reply == null)
                        throw new IOException("network error");
                    boolean done = signIn.handleResponse(reply).done;
                    observer.onNext(done);
                    observer.onCompleted();
                } catch (Exception e) {
                    e.printStackTrace();
                    observer.onError(e);
                    return Subscriptions.empty();
                }
                return Subscriptions.empty();
            }
        });
    }

    class LoginRequest extends RemoteConnectionJob {
        public String signToken = "";

        @Override
        public byte[] getRequest() throws Exception {
            ProtocolOutStream outStream = new ProtocolOutStream();
            Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_GET);

            Element authStanza = outStream.createElement(AUTH_STANZA);
            authStanza.setAttribute("type", "signature");
            authStanza.setAttribute("loginUser", user.getUid());
            authStanza.setAttribute("serverUser", serverUser);
            iqStanza.appendChild(authStanza);

            outStream.addElement(iqStanza);
            return outStream.toBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
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

            signToken = userAuthHandler.signToken;

            return new Result(true);
        }
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

    class SignIn extends RemoteConnectionJob {
        final private LoginRequest loginRequest;

        public SignIn(LoginRequest loginRequest) {
            this.loginRequest = loginRequest;
        }

        @Override
        public byte[] getRequest() throws Exception {
            byte signature[] = user.sign(user.getMainKeyId(), loginRequest.signToken.getBytes());

            ProtocolOutStream outStream = new ProtocolOutStream();
            Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_SET);
            outStream.addElement(iqStanza);
            Element authStanza = outStream.createElement(AUTH_SIGNED_STANZA);
            authStanza.setAttribute("signature", Base64.encodeBytes(signature));
            authStanza.setAttribute("serverUser", serverUser);
            authStanza.setAttribute("loginUser", user.getUid());
            iqStanza.appendChild(authStanza);

            return outStream.toBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            IqInStanzaHandler iqHandler = new IqInStanzaHandler(ProtocolOutStream.IQ_RESULT);
            UserAuthResultHandler userAuthResultHandler = new UserAuthResultHandler();
            AuthResultRoleHandler roleHandler = new AuthResultRoleHandler();
            userAuthResultHandler.addChildHandler(roleHandler);
            iqHandler.addChildHandler(userAuthResultHandler);

            ProtocolInStream inStream = new ProtocolInStream(reply);
            inStream.addHandler(iqHandler);
            inStream.parse();

            if (!userAuthResultHandler.hasBeenHandled())
                return new RemoteConnectionJob.Result(false);

            roles = roleHandler.roles;
            if (roles.size() == 0)
                return new RemoteConnectionJob.Result(false);

            return new RemoteConnectionJob.Result(true);
        }
    }
}
