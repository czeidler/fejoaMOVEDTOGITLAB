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
import org.fejoa.library2.remote.JsonRPC;
import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyPair;


public class ContactRequest {
    final static public String CONTACT_REQUEST_STANZA = "contactRequest";
    final static public String PUBLIC_KEY_STANZA = "publicKey";

    final private ConnectionInfo connectionInfo;
    final private UserIdentity userIdentity;

    public interface IListener {
        void onNewContact(ContactPublic contactPublic);
    }

    public ContactRequest(ConnectionInfo info, UserIdentity identity) {
        this.connectionInfo = info;
        this.userIdentity = identity;
    }

    public JsonContactRequestJob getContactRequestJob() {
        return new JsonContactRequestJob();
    }

    public class JsonContactRequestJob extends JsonRemoteConnectionJob {
        private IListener listener;

        public void setListener(IListener listener) {
            this.listener = listener;
        }

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

            onResponse(uid, address, keyId, publicKey, listener);

            return new Result(Result.DONE, getMessage(result));
        }
    }

    private void onResponse(String uid, String address, String keyId, String publicKey, IListener listener)
            throws IOException,
            CryptoException {

        Contact inListContact = userIdentity.getContactFinder().find(uid);
        if (inListContact != null)
            return;

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

        if (listener != null)
            listener.onNewContact(contact);
    }
}
