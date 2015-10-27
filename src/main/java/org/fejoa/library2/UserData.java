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
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;


public class UserData extends StorageKeyStore {
    final static private String STORAGE_LIST_DIR = "storage";
    final static private String REMOTES_LIST_DIR = "remotes";
    final static private String IDENTITY_KEYS_ID_KEY = "identityKeysId";
    final static private String IN_COMMAND_QUEUE_ID_KEY = "inCommandQueue";
    final static private String OUT_COMMAND_QUEUE_ID_KEY = "outCommandQueue";

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
    private StorageRefList storageRefList;
    private RemoteList remoteList;
    private IdentityKeys identityKeys;
    private IncomingCommandQueue incomingCommandQueue;
    private OutgoingCommandQueue outgoingCommandQueue;


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
        storageRefList = StorageRefList.create(storageListDir);
        addStorage(keyStore.getId());

        // remote list
        StorageDir remotesDir = new StorageDir(storageDir, REMOTES_LIST_DIR);
        remoteList = RemoteList.create(remotesDir);

        identityKeys = IdentityKeys.create(context, CryptoHelper.generateSha1Id(Crypto.get()), keyStore, keyId);
        storageDir.writeString(IDENTITY_KEYS_ID_KEY, identityKeys.getId());
        KeyPair signatureKeyPair = context.getCrypto().generateKeyPair(context.getCryptoSettings().signature);
        String signatureKeyId = identityKeys.addKeyPair(signatureKeyPair, context.getCryptoSettings().signature);
        identityKeys.setDefaultSignatureKey(signatureKeyId);
        KeyPair publicKeyPair = context.getCrypto().generateKeyPair(context.getCryptoSettings().publicKey);
        String publicKeyId = identityKeys.addKeyPair(publicKeyPair, context.getCryptoSettings().publicKey);
        identityKeys.setDefaultPublicKey(publicKeyId);

        // command queues
        String incomingCommandQueueId = CryptoHelper.generateSha1Id(Crypto.get());
        StorageDir inCommandQueueDir = context.getStorage(incomingCommandQueueId);
        incomingCommandQueue = new IncomingCommandQueue(inCommandQueueDir);
        storageDir.writeString(IN_COMMAND_QUEUE_ID_KEY, incomingCommandQueue.getId());

        String outgoingCommandQueueId = CryptoHelper.generateSha1Id(Crypto.get());
        StorageDir outCommandQueueDir = context.getStorage(outgoingCommandQueueId);
        outgoingCommandQueue = new OutgoingCommandQueue(outCommandQueueDir);
        storageDir.writeString(OUT_COMMAND_QUEUE_ID_KEY, outgoingCommandQueue.getId());
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
        storageRefList = StorageRefList.load(storageListDir);

        // remote list
        StorageDir remotesDir = new StorageDir(storageDir, REMOTES_LIST_DIR);
        remoteList = RemoteList.load(remotesDir);

        // identity keys
        String identityKeysId = storageDir.readString(IDENTITY_KEYS_ID_KEY);
        StorageDir identityKeysDir = context.getStorage(identityKeysId);
        identityKeys = IdentityKeys.open(context, identityKeysDir, keyStores);

        // command queues
        String inCommandQueueId = storageDir.readString(IN_COMMAND_QUEUE_ID_KEY);
        incomingCommandQueue = new IncomingCommandQueue(context.getStorage(inCommandQueueId));
        String outCommandQueueId = storageDir.readString(OUT_COMMAND_QUEUE_ID_KEY);
        outgoingCommandQueue = new OutgoingCommandQueue(context.getStorage(outCommandQueueId));
    }

    public void commit() throws IOException {
        keyStore.commit();
        storageDir.commit();
        identityKeys.commit();
    }

    private void addStorage(String storageId) throws IOException {
        storageRefList.add(new StorageRefList.StorageEntry(storageId));
    }

    public RemoteList getRemoteList() {
        return remoteList;
    }

    public IdentityKeys getIdentityKeys() {
        return identityKeys;
    }

    public StorageRefList getStorageRefList() {
        return storageRefList;
    }
}
