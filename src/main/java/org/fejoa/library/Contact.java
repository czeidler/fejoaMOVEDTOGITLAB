/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;


import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.SecureStorageDir;

import java.io.IOException;
import java.security.PublicKey;


abstract public class Contact {
    protected String uid = "";
    protected String server = "";
    protected String serverUser = "";
    protected KeyId mainKeyId;

    final protected SecureStorageDir storageDir;

    public Contact(SecureStorageDir storageDir) {
        this.storageDir = storageDir;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void write() throws IOException, CryptoException {
        storageDir.writeString("uid", uid);
        storageDir.writeString("server", server);
        storageDir.writeString("serverUser", serverUser);
        storageDir.writeString("mainKeyId", mainKeyId.getKeyId());
    }

    public void open() throws IOException, CryptoException {
        uid = storageDir.readString("uid");
        server = storageDir.readString("server");
        serverUser = storageDir.readString("serverUser");
        mainKeyId = new KeyId(storageDir.readString("mainKeyId"));
    }

    abstract public boolean verify(KeyId keyId, byte data[], byte signature[],
                                   CryptoSettings.SignatureSettings signatureSettings) throws CryptoException;
    abstract public PublicKey getPublicKey(KeyId keyId);

    public String getAddress() {
        return serverUser + "@" + server;
    }

    public boolean setAddress(String address) {
        String[] parts = address.split("@");
        if (parts.length != 2)
            return false;
        server = parts[1];
        serverUser = parts[0];
        return true;
    }

    public void setServerUser(String serverUser) {
        this.serverUser = serverUser;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getServer() {
        return server;
    }

    public String getServerUser() {
        return serverUser;
    }

    public void setMainKey(KeyId keyId) {
        mainKeyId = keyId;
    }
    public KeyId getMainKeyId() {
        return mainKeyId;
    }
}
