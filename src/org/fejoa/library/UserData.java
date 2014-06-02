package org.fejoa.library;


public class UserData {
    private String PATH_UID = "uid";
    private String PATH_KEY_STORE_ID = "key_store_id";
    private String PATH_KEY_KEY_ID = "key_id";

    protected String uid;
    protected SecureStorageDir storageDir;

    protected void writeUserData() throws Exception {
        storageDir.writeString(PATH_UID, uid);
        storageDir.writeString(PATH_KEY_KEY_ID, getKeyId().getKeyId());
        storageDir.writeString(PATH_KEY_STORE_ID, getKeyStore().getUid());
    }

    protected void readUserData(SecureStorageDir storageDir, IKeyStoreFinder keyStoreFinder) throws Exception {
        this.storageDir = storageDir;

        uid = storageDir.readString(PATH_UID);
        String keyStoreId = storageDir.readString(PATH_KEY_STORE_ID);
        KeyStore keyStore = keyStoreFinder.find(keyStoreId);
        if (keyStore == null)
            throw new ClassNotFoundException("can't find key store");
        String keyId = storageDir.readString(PATH_KEY_KEY_ID);
        storageDir.setTo(keyStore, new KeyId(keyId));
    }

    protected KeyId getKeyId() {
        return storageDir.getKeyId();
    }

    protected KeyStore getKeyStore() {
        return storageDir.getKeyStore();
    }
}
