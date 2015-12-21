/*
 * Copyright 2014-2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.crypto.*;
import org.fejoa.library2.command.IncomingCommandQueue;
import org.fejoa.library2.command.OutgoingCommandQueue;
import org.fejoa.library2.database.StorageDir;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;


public class UserData extends StorageKeyStore {
    final static private String STORAGE_LIST_DIR = "storage";
    final static private String REMOTES_LIST_DIR = "remotes";
    final static private String IDENTITY_STORE_KEY = "identity";
    final static private String CONTACT_STORE_KEY = "contacts";
    final static public String IN_COMMAND_QUEUE_ID_KEY = "inCommandQueue";
    final static private String OUT_COMMAND_QUEUE_ID_KEY = "outCommandQueue";

    static public UserData create(FejoaContext context, String password) throws IOException, CryptoException {
        return create(context, CryptoHelper.generateSha1Id(Crypto.get()), password);
    }

    static private UserData create(FejoaContext context, String id, String password) throws IOException,
            CryptoException {
        context.setUserDataId(id);
        StorageDir dir = context.getStorage(id);
        UserData userData = new UserData(context, dir);
        userData.create(password);
        return userData;
    }

    static public UserData open(FejoaContext context, String password) throws IOException,
            CryptoException {
        String id = context.getUserDataId();
        StorageDir dir = context.getStorage(id);
        UserData userData = new UserData(context, dir);
        userData.open(password);
        return userData;
    }

    final private List<KeyStore> keyStores = new ArrayList<>();
    final private StorageDirList<Storage> storageRefList = new StorageDirList<>(
            new StorageDirList.AbstractEntryIO<Storage>() {
        @Override
        public String getId(Storage entry) {
            return entry.getId();
        }

        @Override
        public Storage read(StorageDir dir) throws IOException {
            Storage entry = new Storage();
            entry.read(dir);
            return entry;
        }
    });
    final private StorageDir plainTextDir;
    private RemoteList remoteList;
    private IdentityStore identityStore;
    private ContactStore contactStore;
    private IncomingCommandQueue incomingCommandQueue;
    private OutgoingCommandQueue outgoingCommandQueue;

    private UserData(FejoaContext context, StorageDir dir) {
        super(context, dir);

        plainTextDir = new StorageDir(dir);
        plainTextDir.setFilter(null);
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
        storageRefList.setTo(storageListDir);

        addStorage(getId());
        addStorage(keyStore.getId());

        // remote list
        StorageDir remotesDir = new StorageDir(storageDir, REMOTES_LIST_DIR);
        remoteList = new RemoteList(remotesDir);

        // identity
        identityStore = IdentityStore.create(context, CryptoHelper.generateSha1Id(Crypto.get()), keyStore, keyId);
        storageDir.writeString(IDENTITY_STORE_KEY, identityStore.getId());
        KeyPair signatureKeyPair = context.getCrypto().generateKeyPair(context.getCryptoSettings().signature);
        String signatureKeyId = identityStore.addSignatureKeyPair(signatureKeyPair,
                context.getCryptoSettings().signature);
        identityStore.setDefaultSignatureKey(signatureKeyId);
        identityStore.getMyself().setId(signatureKeyId);
        KeyPair publicKeyPair = context.getCrypto().generateKeyPair(context.getCryptoSettings().publicKey);
        String publicKeyId = identityStore.addEncryptionKeyPair(publicKeyPair, context.getCryptoSettings().publicKey);
        identityStore.setDefaultEncryptionKey(publicKeyId);
        addStorage(identityStore.getId());

        // contacts
        contactStore = ContactStore.create(context, CryptoHelper.generateSha1Id(Crypto.get()), keyStore, keyId);
        storageDir.writeString(CONTACT_STORE_KEY, contactStore.getId());
        addStorage(contactStore.getId());

        // command queues
        String incomingCommandQueueId = CryptoHelper.generateSha1Id(Crypto.get());
        StorageDir inCommandQueueDir = context.getStorage(incomingCommandQueueId);
        incomingCommandQueue = new IncomingCommandQueue(inCommandQueueDir);
        plainTextDir.writeString(IN_COMMAND_QUEUE_ID_KEY, incomingCommandQueue.getId());
        addStorage(incomingCommandQueue.getId());

        String outgoingCommandQueueId = CryptoHelper.generateSha1Id(Crypto.get());
        StorageDir outCommandQueueDir = context.getStorage(outgoingCommandQueueId);
        outgoingCommandQueue = new OutgoingCommandQueue(outCommandQueueDir);
        storageDir.writeString(OUT_COMMAND_QUEUE_ID_KEY, outgoingCommandQueue.getId());
        addStorage(outgoingCommandQueue.getId());
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
        storageRefList.setTo(storageListDir);

        // ourselves to the list
        addStorage(getId());

        // remote list
        StorageDir remotesDir = new StorageDir(storageDir, REMOTES_LIST_DIR);
        remoteList.setTo(remotesDir);

        // identity keys
        String identityKeysId = storageDir.readString(IDENTITY_STORE_KEY);
        StorageDir identityKeysDir = context.getStorage(identityKeysId);
        identityStore = IdentityStore.open(context, identityKeysDir, keyStores);
        addStorage(identityStore.getId());

        // contacts
        String contactStoreId = storageDir.readString(CONTACT_STORE_KEY);
        StorageDir contactStoreDir = context.getStorage(contactStoreId);
        contactStore = ContactStore.open(context, contactStoreDir, keyStores);
        addStorage(contactStore.getId());

        // command queues
        String inCommandQueueId = plainTextDir.readString(IN_COMMAND_QUEUE_ID_KEY);
        incomingCommandQueue = new IncomingCommandQueue(context.getStorage(inCommandQueueId));
        addStorage(incomingCommandQueue.getId());
        String outCommandQueueId = storageDir.readString(OUT_COMMAND_QUEUE_ID_KEY);
        outgoingCommandQueue = new OutgoingCommandQueue(context.getStorage(outCommandQueueId));
        addStorage(outgoingCommandQueue.getId());
    }

    public void commit() throws IOException {
        storageDir.commit();

        keyStore.commit();
        identityStore.commit();
        contactStore.commit();

        incomingCommandQueue.commit();
        outgoingCommandQueue.commit();
    }

    private void addStorage(String storageId) throws IOException {
        storageRefList.add(new Storage(storageId));
    }

    public RemoteList getRemoteList() {
        return remoteList;
    }

    public IdentityStore getIdentityStore() {
        return identityStore;
    }

    public ContactStore getContactStore() {
        return contactStore;
    }

    public StorageDirList<Storage> getStorageRefList() {
        return storageRefList;
    }

    public IncomingCommandQueue getIncomingCommandQueue() {
        return incomingCommandQueue;
    }

    public OutgoingCommandQueue getOutgoingCommandQueue() {
        return outgoingCommandQueue;
    }
}
