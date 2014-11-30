/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.SecureStorageDir;

import java.io.IOException;


public class UserData implements IStorageUid {
    private String PATH_UID = "uid";
    private String PATH_KEY_STORE_ID = "keyStoreId";
    private String PATH_KEY_ID = "keyId";

    protected String uid;
    protected SecureStorageDir storageDir;

    public void commit() throws IOException {
        storageDir.commit();
    }

    protected void setStorageDir(SecureStorageDir storageDir) {
        this.storageDir = storageDir;
    }

    protected void writeUserData(String uid, SecureStorageDir storageDir)
            throws IOException, CryptoException {
        this.uid = uid;
        setStorageDir(storageDir);

        storageDir.writeString(PATH_UID, uid);
        storageDir.writeString(PATH_KEY_ID, getKeyId().getKeyId());
        storageDir.writeString(PATH_KEY_STORE_ID, getKeyStore().getUid());
    }

    protected void readUserData(SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder)
            throws IOException,
            CryptoException {
        setStorageDir(storageDir);

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

    @Override
    public String getUid() {
        return uid;
    }

    @Override
    public SecureStorageDir getStorageDir() {
        return storageDir;
    }
}
