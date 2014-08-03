/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;


public class UserData {
    private String PATH_UID = "uid";
    private String PATH_KEY_STORE_ID = "key_store_id";
    private String PATH_KEY_ID = "key_id";

    protected String uid;
    protected SecureStorageDir storageDir;

    public void commit() throws IOException {
        storageDir.commit();
    }

    protected void writeUserData(String uid, SecureStorageDir storageDir)
            throws IOException, CryptoException {
        this.uid = uid;
        this.storageDir = storageDir;

        storageDir.writeString(PATH_UID, uid);
        storageDir.writeString(PATH_KEY_ID, getKeyId().getKeyId());
        storageDir.writeString(PATH_KEY_STORE_ID, getKeyStore().getUid());
    }

    protected void readUserData(SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder)
            throws IOException,
            CryptoException {
        this.storageDir = storageDir;

        uid = storageDir.readString(PATH_UID);

        String keyStoreId = storageDir.readString(PATH_KEY_STORE_ID);
        KeyStore keyStore = keyStoreFinder.find(keyStoreId);
        if (keyStore == null)
            throw new IOException("can't find key store");
        String keyId = storageDir.readString(PATH_KEY_ID);
        storageDir.setTo(keyStore, new KeyId(keyId));
    }

    protected boolean readUserData(SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder, String password)
            throws IOException,
            CryptoException {
        this.storageDir = storageDir;

        uid = storageDir.readString(PATH_UID);

        String keyStoreId = storageDir.readString(PATH_KEY_STORE_ID);
        KeyStore keyStore = keyStoreFinder.find(keyStoreId);
        if (keyStore == null)
            throw new IOException("can't find key store");
        if (!keyStore.open(password))
            return false;
        String keyId = storageDir.readString(PATH_KEY_ID);
        storageDir.setTo(keyStore, new KeyId(keyId));

        return true;
    }

    protected KeyId getKeyId() {
        return storageDir.getKeyId();
    }

    protected KeyStore getKeyStore() {
        return storageDir.getKeyStore();
    }

    public String getUid() {
        return uid;
    }

    public SecureStorageDir getStorageDir() {
        return storageDir;
    }
}
