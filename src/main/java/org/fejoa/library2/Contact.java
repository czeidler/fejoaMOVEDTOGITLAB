/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.*;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;


interface IContactPublic {
    boolean verify(KeyId keyId, byte[] data, byte[] signature, CryptoSettings.Signature signatureSettings)
            throws CryptoException;
}

interface IContactPrivate extends IContactPublic {
    byte[] sign(KeyId keyId, byte data[], CryptoSettings.Signature signatureSettings) throws CryptoException;
}


abstract class Contact<T> implements IContactPublic {
    final static private String SIGNATURE_KEYS_DIR = "signatureKeys";
    final static private String ENCRYPTION_KEYS_DIR = "encryptionKeys";

    final protected FejoaContext context;
    final protected StorageDirList.IEntryIO<T> entryIO;
    protected StorageDir storageDir;

    protected String id = "";

    protected StorageDirList<T> signatureKeys;
    protected StorageDirList<T> encryptionKeys;

    protected Contact(FejoaContext context, StorageDirList.IEntryIO<T> entryIO, StorageDir dir) {
        this.context = context;
        this.entryIO = entryIO;

        if (dir != null)
            setStorageDir(dir);
    }

    protected void setStorageDir(StorageDir dir) {
        this.storageDir = dir;

        try {
            id = storageDir.readString(Constants.ID_KEY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        signatureKeys = new StorageDirList<>(new StorageDir(dir, SIGNATURE_KEYS_DIR), entryIO);
        encryptionKeys = new StorageDirList<>(new StorageDir(dir, ENCRYPTION_KEYS_DIR), entryIO);
    }

    abstract public PublicKey getVerificationKey(KeyId keyId);

    @Override
    public boolean verify(KeyId keyId, byte[] data, byte[] signature, CryptoSettings.Signature signatureSettings)
            throws CryptoException {
        ICryptoInterface crypto = context.getCrypto();
        PublicKey publicKey = getVerificationKey(keyId);
        return crypto.verifySignature(data, signature, publicKey, signatureSettings);
    }

    public void setId(String id) throws IOException {
        this.id = id;
        storageDir.writeString(Constants.ID_KEY, id);
    }

    public String getId() {
        return id;
    }

    public StorageDirList<T> getSignatureKeys() {
        return signatureKeys;
    }

    public StorageDirList<T> getEncryptionKeys() {
        return encryptionKeys;
    }

    public void addSignatureKey(T key) throws IOException {
        signatureKeys.add(key);
    }

    public T getSignatureKey(KeyId id) {
        return signatureKeys.get(id.toString());
    }

    public void addEncryptionKey(T key) throws IOException {
        encryptionKeys.add(key);
    }

    public T getEncryptionKey(KeyId id) {
        return encryptionKeys.get(id.toString());
    }
}


class ContactPublic extends Contact<PublicKeyItem> {
    final static private String REMOTES_DIR = "remotes";

    private RemoteList remotes;

    public ContactPublic(FejoaContext context) {
        super(context, getEntryIO(), null);
    }

    protected ContactPublic(FejoaContext context, StorageDir storageDir) {
        super(context, getEntryIO(), storageDir);
    }

    static private StorageDirList.AbstractEntryIO<PublicKeyItem> getEntryIO() {
        return new StorageDirList.AbstractEntryIO<PublicKeyItem>() {
            @Override
            public String getId(PublicKeyItem entry) {
                return entry.getId();
            }

            @Override
            public PublicKeyItem read(StorageDir dir) throws IOException {
                PublicKeyItem item = new PublicKeyItem();
                item.read(dir);
                return item;
            }
        };
    }

    @Override
    protected void setStorageDir(StorageDir dir) {
        super.setStorageDir(dir);

        remotes = new RemoteList(new StorageDir(storageDir, REMOTES_DIR));
    }

    @Override
    public PublicKey getVerificationKey(KeyId keyId) {
        return signatureKeys.get(keyId.toString()).getKey();
    }
}

class ContactPrivate extends Contact<KeyPairItem> implements IContactPrivate {
    protected ContactPrivate(FejoaContext context, StorageDir dir) {
        super(context, new StorageDirList.AbstractEntryIO<KeyPairItem>() {
            @Override
            public String getId(KeyPairItem entry) {
                return entry.getId();
            }

            @Override
            public KeyPairItem read(StorageDir dir) throws IOException {
                KeyPairItem keyPairItem = new KeyPairItem();
                keyPairItem.read(dir);
                return keyPairItem;
            }
        }, dir);
    }

    @Override
    public PublicKey getVerificationKey(KeyId keyId) {
        return signatureKeys.get(keyId.toString()).getKeyPair().getPublic();
    }

    @Override
    public byte[] sign(KeyId keyId, byte[] data, CryptoSettings.Signature signatureSettings) throws CryptoException {
        ICryptoInterface crypto = context.getCrypto();
        PrivateKey publicKey = signatureKeys.get(keyId.toString()).getKeyPair().getPrivate();
        return crypto.sign(data, publicKey, signatureSettings);
    }
}
