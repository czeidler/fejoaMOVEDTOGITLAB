/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.InStanzaHandler;
import org.fejoa.library.support.IqInStanzaHandler;
import org.fejoa.library.support.ProtocolInStream;
import org.fejoa.library.support.ProtocolOutStream;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.security.KeyPair;


class ContactRequestHandler extends InStanzaHandler {
    public String status;
    public String uid;
    public String address;
    public String keyId;

    public ContactRequestHandler() {
        super(ContactRequest.CONTACT_REQUEST_STANZA, false);
    }

    @Override
    public boolean handleStanza(Attributes attributes) {
        if (attributes.getIndex("status") < 0)
            return false;
        status = attributes.getValue("status");
        if (!status.equals("ok"))
            return true;

        if (attributes.getIndex("uid") < 0)
            return false;
        if (attributes.getIndex("keyId") < 0)
            return false;
        if (attributes.getIndex("address") < 0)
            return false;

        uid = attributes.getValue("uid");
        keyId = attributes.getValue("keyId");
        address = attributes.getValue("address");

        if (uid.equals(""))
            return false;

        return true;
    }
}

class PublicKeyHandler extends InStanzaHandler {
    public String publicKey;

    public PublicKeyHandler() {
        super(ContactRequest.PUBLIC_KEY_STANZA, true);
    }

    @Override
    public boolean handleStanza(Attributes attributes) {
        return true;
    }

    @Override
    public boolean handleText(String text) {
        publicKey = text.toString();
        return true;
    }
}


public class ContactRequest {
    final static public String CONTACT_REQUEST_STANZA = "contactRequest";
    final static public String PUBLIC_KEY_STANZA = "publicKey";

    private ConnectionInfo connectionInfo;
    private UserIdentity userIdentity;

    public ContactRequest(ConnectionInfo info, UserIdentity identity) {
        this.connectionInfo = info;
        this.userIdentity = identity;
    }

    public RemoteConnectionJob getContactRequestJob() {
        return new JsonContactRequestJob();
    }

    class JsonContactRequestJob extends JsonRemoteConnectionJob {

        @Override
        public byte[] getRequest() throws Exception {
            ContactPrivate myself = userIdentity.getMyself();
            KeyId keyId = userIdentity.getMyself().getMainKeyId();
            KeyPair keyPair = userIdentity.getMyself().getKeyPair(keyId.getKeyId());

            return jsonRPC.call(CONTACT_REQUEST_STANZA,
                    new JsonRPC.Argument("serverUser", connectionInfo.serverUser),
                    new JsonRPC.Argument("uid", myself.getUid()),
                    new JsonRPC.Argument("keyId", keyId.getKeyId()),
                    new JsonRPC.Argument("address", myself.getAddress()),
                    new JsonRPC.Argument(PUBLIC_KEY_STANZA, CryptoHelper.convertToPEM(keyPair.getPublic())))
                    .getBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            JSONObject result = jsonRPC.getReturnValue(new String(reply));
            if (getStatus(result) != 0)
                return new Result(Result.ERROR, getMessage(result));

            if (!result.has("uid") || !result.has("address") || !result.has("keyId") || !result.has(PUBLIC_KEY_STANZA))
                return new Result(Result.ERROR, "invalid arguments");

            String uid = result.getString("uid");
            String address = result.getString("address");
            String keyId = result.getString("keyId");
            String publicKey = result.getString(PUBLIC_KEY_STANZA);

            if (uid.equals(""))
                return new Result(Result.ERROR, "bad uid");

            onResponse(uid, address, keyId, publicKey);

            return new Result(Result.DONE, getMessage(result));
        }
    }

    private void onResponse(String uid, String address, String keyId, String publicKey) throws IOException,
            CryptoException {
        ContactPublic contact = userIdentity.addNewContact(uid);
        contact.addKey(keyId, CryptoHelper.publicKeyFromPem(publicKey));
        contact.setMainKey(new KeyId(keyId));
        if (!address.equals(""))
            contact.setAddress(address);
        else
            contact.setAddress(connectionInfo.getRemote());
        contact.write();

        // commit changes
        userIdentity.commit();
    }

    public class ContactRequestJob extends RemoteConnectionJob {
        @Override
        public byte[] getRequest() throws Exception {
            ProtocolOutStream outStream = new ProtocolOutStream();
            Element iqStanza = outStream.createIqElement(ProtocolOutStream.IQ_GET);
            outStream.addElement(iqStanza);
            Element requestStanza = outStream.createElement(CONTACT_REQUEST_STANZA);
            iqStanza.appendChild(requestStanza);

            ContactPrivate myself = userIdentity.getMyself();
            KeyId keyId = userIdentity.getMyself().getMainKeyId();
            KeyPair keyPair = userIdentity.getMyself().getKeyPair(keyId.getKeyId());

            requestStanza.setAttribute("serverUser", connectionInfo.serverUser);
            requestStanza.setAttribute("uid", myself.getUid());
            requestStanza.setAttribute("keyId", keyId.getKeyId());
            requestStanza.setAttribute("address", myself.getAddress());

            Element publicKeyStanza = outStream.createElement(PUBLIC_KEY_STANZA);
            publicKeyStanza.setTextContent(CryptoHelper.convertToPEM(keyPair.getPublic()));
            requestStanza.appendChild(publicKeyStanza);

            return outStream.toBytes();
        }

        @Override
        public Result handleResponse(byte[] reply) throws Exception {
            IqInStanzaHandler iqHandler = new IqInStanzaHandler(ProtocolOutStream.IQ_RESULT);
            ContactRequestHandler requestHandler = new ContactRequestHandler();
            PublicKeyHandler publicKeyHandler = new PublicKeyHandler();
            iqHandler.addChildHandler(requestHandler);
            requestHandler.addChildHandler(publicKeyHandler);

            ProtocolInStream inStream = new ProtocolInStream(reply);
            inStream.addHandler(iqHandler);
            inStream.parse();

            if (!requestHandler.hasBeenHandled())
                return new Result(Result.ERROR);

            if (!requestHandler.status.equals("ok"))
                return new Result(Result.ERROR, requestHandler.status);

            if (!publicKeyHandler.hasBeenHandled())
                return new Result(Result.ERROR);

            onResponse(requestHandler.uid, requestHandler.address, requestHandler.keyId, publicKeyHandler.publicKey);

            return new Result(Result.DONE);
        }
    }
}
