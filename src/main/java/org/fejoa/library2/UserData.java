/*
 * Copyright 2014-2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.crypto.*;
import org.fejoa.library2.database.StorageDir;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class UserData extends StorageKeyStore {
    final static private String STORAGE_LIST_DIR = "storage";
    final static private String REMOTES_LIST_DIR = "remotes";

    static public UserData create(FejoaContext context, String password) throws IOException, CryptoException {
        return create(context, CryptoHelper.generateSha1Id(Crypto.get()), password);
    }

    static public UserData create(FejoaContext context, String id, String password) throws IOException,
            CryptoException {
        StorageDir dir = context.getStorage(id);
        UserData userData = new UserData(context, dir);
        userData.create(password);
        return userData;
    }

    static public UserData open(FejoaContext context, StorageDir dir, String password) throws IOException,
            CryptoException {
        UserData userData = new UserData(context, dir);
        userData.open(password);
        return userData;
    }

    final private List<KeyStore> keyStores = new ArrayList<>();
    private StorageList storageList;
    private RemoteList remoteList;

    private UserData(FejoaContext context, StorageDir dir) {
        super(context, dir);
    }

    private void create(String password) throws IOException, CryptoException {
        // setup encryption
        keyStore = KeyStore.create(context, CryptoHelper.generateSha1Id(Crypto.get()), password);
        keyStores.add(keyStore);
        ICryptoInterface crypto = context.getCrypto();
        CryptoSettings cryptoSettings = context.getCryptoSettings();
        CryptoSettings.Symmetric symmetricSettings = cryptoSettings.symmetric;

        SecretKey secretKey = crypto.generateSymmetricKey(symmetricSettings);
        byte[] iv = crypto.generateInitializationVector(symmetricSettings.ivSize);
        keyId = keyStore.writeSymmetricKey(secretKey, iv, symmetricSettings.keyType);

        super.create(keyStore, keyId);

        // storage list
        StorageDir storageListDir = new StorageDir(storageDir, STORAGE_LIST_DIR);
        storageList = StorageList.create(storageListDir);
        addStorage(keyStore.getId());

        // remote list
        StorageDir remotesDir = new StorageDir(storageDir, REMOTES_LIST_DIR);
        remoteList = RemoteList.create(remotesDir);
    }

    private void open(String password) throws IOException, CryptoException {
        // setup encryption
        String keyStoreId = storageDir.readString(KEY_STORES_ID_KEY);
        StorageDir keyStoreDir = context.getStorage(keyStoreId);
        keyStore = KeyStore.open(context, keyStoreDir, password);
        keyStores.add(keyStore);

        super.open(keyStores);

        // storage list
        StorageDir storageListDir = new StorageDir(storageDir, STORAGE_LIST_DIR);
        storageList = StorageList.load(storageListDir);

        // remote list
        StorageDir remotesDir = new StorageDir(storageDir, REMOTES_LIST_DIR);
        remoteList = RemoteList.load(remotesDir);
    }

    public void commit() throws IOException {
        keyStore.commit();
        storageDir.commit();
    }

    public String getId() {
        return storageDir.getBranch();
    }

    private void addStorage(String storageId) throws IOException {
        storageList.add(new StorageList.StorageEntry(storageId));
    }

    public RemoteList getRemoteList() {
        return remoteList;
    }
}
