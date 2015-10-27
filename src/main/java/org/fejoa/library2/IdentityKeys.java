/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library2.database.StorageDir;
import org.fejoa.library2.util.CryptoSettingsIO;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;


class AsymKeyList extends AbstractStorageDirList<AsymKeyList.Entry> {
    static class Entry implements IStorageDirBundle {
        final private String PATH_PRIVATE_KEY = "privateKey";
        final private String PATH_PUBLIC_KEY = "publicKey";

        private String id;
        private KeyPair keyPair;
        private CryptoSettings.KeyTypeSettings typeSettings;

        private Entry() {
            typeSettings = new CryptoSettings.KeyTypeSettings();
        }

        public Entry(KeyPair keyPair, CryptoSettings.KeyTypeSettings typeSettings) {
            this.id = CryptoHelper.generateSha1Id(Crypto.get());
            this.keyPair = keyPair;
            this.typeSettings = typeSettings;
        }

        @Override
        public void write(StorageDir dir) throws IOException {
            String publicKeyPem = CryptoHelper.convertToPEM(keyPair.getPublic());
            String privateKeyPem = CryptoHelper.convertToPEM(keyPair.getPrivate());
            dir.writeString(Constants.ID_KEY, id);
            dir.writeString(PATH_PRIVATE_KEY, privateKeyPem);
            dir.writeString(PATH_PUBLIC_KEY, publicKeyPem);
            CryptoSettingsIO.write(typeSettings, dir, "");
        }

        @Override
        public void read(StorageDir dir) throws IOException {
            String privateKeyPem = dir.readString(PATH_PRIVATE_KEY);
            String publicKeyPem = dir.readString(PATH_PUBLIC_KEY);
            CryptoSettingsIO.read(typeSettings, dir, "");

            id = dir.readString(Constants.ID_KEY);
            PrivateKey privateKey = CryptoHelper.privateKeyFromPem((privateKeyPem));
            PublicKey publicKey = CryptoHelper.publicKeyFromPem(publicKeyPem);
            keyPair = new KeyPair(publicKey, privateKey);
        }

        public String getId() {
            return id;
        }

        public KeyPair getKeyPair() {
            return keyPair;
        }
    }

    static public AsymKeyList create(StorageDir storageDir) {
        return new AsymKeyList(storageDir);
    }

    static public AsymKeyList load(StorageDir dir) {
        AsymKeyList list = new AsymKeyList(dir);
        list.load();
        return list;
    }

    protected AsymKeyList(StorageDir storageDir) {
        super(storageDir);
    }

    @Override
    protected Entry instantiate(StorageDir dir) {
        return new Entry();
    }
}

public class IdentityKeys extends StorageKeyStore {
    final static private String KEY_LIST_DIR = "keys";
    final static private String DEFAULT_SIGNATURE_KEY_KEY = "defaultCert";
    final static private String DEFAULT_PUBLIC_KEY_KEY = "defaultCrypto";

    private AsymKeyList keyList;
    private String defaultSignatureKey = "";
    private String defaultPublicKey = "";

    static public IdentityKeys create(FejoaContext context, String id, KeyStore keyStore, KeyId keyId)
            throws IOException, CryptoException {
        StorageDir dir = context.getStorage(id);
        IdentityKeys storage = new IdentityKeys(context, dir);
        storage.create(keyStore, keyId);
        return storage;
    }

    static public IdentityKeys open(FejoaContext context, StorageDir dir, List<KeyStore> keyStores) throws IOException,
            CryptoException {
        IdentityKeys storage = new IdentityKeys(context, dir);
        storage.open(keyStores);
        return storage;
    }

    protected IdentityKeys(FejoaContext context, StorageDir dir) {
        super(context, dir);
    }

    @Override
    protected void create(KeyStore keyStore, KeyId keyId) throws IOException, CryptoException {
        super.create(keyStore, keyId);

        StorageDir keyListDir = new StorageDir(storageDir, KEY_LIST_DIR);
        keyList = AsymKeyList.create(keyListDir);
    }

    @Override
    protected void open(List<KeyStore> keyStores) throws IOException, CryptoException {
        super.open(keyStores);

        StorageDir keyListDir = new StorageDir(storageDir, KEY_LIST_DIR);
        keyList = AsymKeyList.load(keyListDir);

        try {
            defaultSignatureKey = storageDir.readString(DEFAULT_SIGNATURE_KEY_KEY);
        } catch (IOException e) {
        }
        try {
            defaultPublicKey = storageDir.readString(DEFAULT_PUBLIC_KEY_KEY);
        } catch (IOException e) {
        }
    }

    public String addKeyPair(KeyPair pair, CryptoSettings.KeyTypeSettings keyTypeSettings) throws IOException {
        AsymKeyList.Entry entry = new AsymKeyList.Entry(pair, keyTypeSettings);
        keyList.add(entry);
        return entry.getId();
    }

    public KeyPair getKeyPair(String keyId) {
        for (AsymKeyList.Entry entry : keyList.map.values()) {
            if (entry.getId().equals(keyId))
                return entry.getKeyPair();
        }
        return null;
    }

    public void setDefaultSignatureKey(String keyId) throws IOException {
        KeyPair pair = getKeyPair(keyId);
        if (pair == null)
            throw new IOException("unknown key");
        storageDir.writeString(DEFAULT_SIGNATURE_KEY_KEY, keyId);
        defaultSignatureKey = keyId;
    }

    public String getDefaultSignatureKey() {
        return defaultSignatureKey;
    }

    public void setDefaultPublicKey(String keyId) throws IOException {
        KeyPair pair = getKeyPair(keyId);
        if (pair == null)
            throw new IOException("unknown key");
        storageDir.writeString(DEFAULT_PUBLIC_KEY_KEY, keyId);
        defaultPublicKey = keyId;
    }

    public String getDefaultPublicKey() {
        return defaultPublicKey;
    }
}

