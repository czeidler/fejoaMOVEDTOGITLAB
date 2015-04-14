/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.*;
import org.fejoa.library.database.FejoaEnvironment;
import org.fejoa.library.database.SecureStorageDir;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.mailbox.Mailbox;
import org.fejoa.library.remote.RemoteStorageLink;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Profile extends UserData {
    final FejoaEnvironment environment;
    final private List<KeyStore> keyStoreList = new ArrayList<>();
    final private List<UserIdentity> userIdentityList = new ArrayList<>();
    final private List<Mailbox> mailboxList = new ArrayList<>();
    final private Map<StorageDir, RemoteStorageLink> remoteStorageLinks = new HashMap<>();
    private UserIdentity mainUserIdentity = null;
    private Mailbox mainMailbox = null;

    final static private String KEY_STORES_BRANCH = "keyStores";
    final static private String USER_IDENTITIES_BRANCH = "userIdentities";
    final static private String MAILBOXES_BRANCH = "mailboxes";
    final static private String PATH_MAIN_USER_IDENTITY = "mainUserIdentity";
    final static private String PATH_MAIN_MAILBOX = "mainMailbox";
    final static private String PATH_KEY_STORES = "keyStores";
    final static private String PATH_USER_IDS = "userIds";
    final static private String PATH_MAILBOXES = "mailboxes";
    final static private String PATH_REMOTES = "remotes";
    final static private String PATH_STORAGE_UID = "storageUid";
    final static private String PATH_STORAGE_BRANCH = "storageBranch";
    final static private String PATH_STORAGE_BASE_DIR = "storageBaseDir";

    final static public String SIGNATURE_FILE = "signature.pup";

    private class KeyStoreFinder implements IKeyStoreFinder {
        final List<KeyStore> keyStoreList;

        public KeyStoreFinder(List<KeyStore> keyStoreList) {
            this.keyStoreList = keyStoreList;
        }

        @Override
        public KeyStore find(String keyStoreId) {
            for (KeyStore keyStore : keyStoreList) {
                if (keyStore.getUid().equals(keyStoreId))
                    return keyStore;
            }
            return null;
        }
    }

    public Profile(FejoaEnvironment environment, String branch, String baseDir) throws IOException {
        this.environment = environment;
        this.storageDir = new SecureStorageDir(environment.getDefault(branch), baseDir, true);
    }

    public Map<StorageDir, RemoteStorageLink> getRemoteStorageLinks() {
        return remoteStorageLinks;
    }

    public UserIdentity getMainUserIdentity() {
        return mainUserIdentity;
    }

    public Mailbox getMainMailbox() {
        return mainMailbox;
    }

    public void createNew(String password) throws IOException, CryptoException {
        ICryptoInterface crypto = Crypto.get();
        uid = CryptoHelper.toHex(crypto.generateInitializationVector(20));

        // init key store and master key
        KeyStore keyStore = new KeyStore(password);
        SecureStorageDir keyStoreBranch = environment.get(storageDir.getDatabasePath(),
                KEY_STORES_BRANCH);
        keyStore.create(new StorageDir(keyStoreBranch, keyStore.getUid(), true));
        addAndWriteKeyStore(keyStore);

        SecretKey key = crypto.generateSymmetricKey(CryptoSettings.SYMMETRIC_KEY_SIZE);
        KeyId keyId = keyStore.writeSymmetricKey(key, crypto.generateInitializationVector(
                CryptoSettings.SYMMETRIC_KEY_IV_SIZE));
        storageDir.setTo(keyStore, keyId);

        UserIdentity userIdentity = new UserIdentity();
        SecureStorageDir userIdBranch = environment.get(storageDir.getDatabasePath(),
                USER_IDENTITIES_BRANCH);
        SecureStorageDir userIdDir = new SecureStorageDir(userIdBranch, keyId.getKeyId(), true);
        userIdDir.setTo(keyStore, keyId);
        userIdentity.write(userIdDir);
        UserIdentity.writePublicSignature(new File(environment.getHomeDir(), SIGNATURE_FILE), userIdentity);

        addAndWriteUseIdentity(userIdentity);
        mainUserIdentity = userIdentity;
        storageDir.writeSecureString(PATH_MAIN_USER_IDENTITY, mainUserIdentity.getUid());

        Mailbox mailbox = new Mailbox(environment, mainUserIdentity);
        SecureStorageDir mailboxesBranch = environment.get(storageDir.getDatabasePath(), MAILBOXES_BRANCH);
        SecureStorageDir mailboxDir = new SecureStorageDir(mailboxesBranch, mailbox.getUid());
        mailboxDir.setTo(keyStore, keyId);
        mailbox.write(mailboxDir);
        addAndWriteMailbox(mailbox);
        mainMailbox = mailbox;
        storageDir.writeString(PATH_MAIN_MAILBOX, mainMailbox.getUid());

        // ourself
        addAndWriteRemoteStorageLink(this, userIdentity.getMyself());
        // other branches
        addAndWriteRemoteStorageLink(keyStore, userIdentity.getMyself());
        addAndWriteRemoteStorageLink(userIdentity, userIdentity.getMyself());
        addAndWriteRemoteStorageLink(mailbox, userIdentity.getMyself());

        writeUserData(uid, storageDir);
    }

    @Override
    public void commit() throws IOException {
        super.commit();

        for (KeyStore keyStore : keyStoreList)
            keyStore.getStorageDir().commit();

        for (UserIdentity entry : userIdentityList)
            entry.getStorageDir().commit();

        for (Mailbox entry : mailboxList)
            entry.getStorageDir().commit();
    }

    public void setEmptyRemotes(String server, String serverUser, ContactPrivate myself) throws IOException, CryptoException {
        for (RemoteStorageLink link : remoteStorageLinks.values()) {
            if (link.getConnectionInfo() != null)
                continue;
            link.setConnectionInfo(server, serverUser, myself);
            link.write();
        }
    }

    public boolean open(String password) throws IOException, CryptoException {
        loadKeyStores();

        if (!readUserData(storageDir, getKeyStoreFinder(), password))
            return false;

        loadUserIdentities();

        // must be called after mainUserIdentity has been found
        loadRemoteStorageLinks();

        loadMailboxes();
        return true;
    }

    public IKeyStoreFinder getKeyStoreFinder() {
        return new KeyStoreFinder(keyStoreList);
    }

    public IUserIdentityFinder getUserIdentityFinder() {
        return new IUserIdentityFinder() {
            @Override
            public UserIdentity find(String uid) {
                for (UserIdentity userIdentity : getUserIdentityList()) {
                    if (userIdentity.getUid().equals(uid))
                        return userIdentity;
                }
                return null;
            }
        };
    }

    public List<UserIdentity> getUserIdentityList() {
        return userIdentityList;
    }

    private void addAndWriteKeyStore(KeyStore keyStore) throws IOException {
        String path = PATH_KEY_STORES + "/";
        path += keyStore.getUid();
        path += "/";

        writeRef(path, keyStore.getUid(), keyStore.getStorageDir());
        keyStoreList.add(keyStore);
    }

    private void addAndWriteUseIdentity(UserIdentity userIdentity) throws IOException {
        String path = PATH_USER_IDS + "/";
        path += userIdentity.getUid();
        path += "/";

        writeRef(path, userIdentity.getUid(), userIdentity.getStorageDir());
        userIdentityList.add(userIdentity);
    }

    private void addAndWriteMailbox(Mailbox mailbox) throws IOException {
        String path = PATH_MAILBOXES + "/";
        path += mailbox.getUid();
        path += "/";

        writeRef(path, mailbox.getUid(), mailbox.getStorageDir());
        mailboxList.add(mailbox);
    }

    private void addAndWriteRemoteStorageLink(IStorageUid localStorage, ContactPrivate myself)
            throws IOException, CryptoException {
        if (remoteStorageLinks.containsKey(localStorage))
            return;
        RemoteStorageLink remoteStorageLink = new RemoteStorageLink(new SecureStorageDir(storageDir, PATH_REMOTES),
                localStorage, myself);
        remoteStorageLink.write();
        remoteStorageLinks.put(localStorage.getStorageDir(), remoteStorageLink);
    }

    private void loadKeyStores() throws IOException {
        List<String> keyStores = storageDir.listDirectories(PATH_KEY_STORES);

        for (String keyStorePath : keyStores) {
            UserDataRef ref = readRef(PATH_KEY_STORES + "/" + keyStorePath);
            // use a secure storage without a key store (hack to use SecureStorageDirBucket)
            StorageDir dir = new SecureStorageDir(environment.getByStorageId(ref.branchUid, ref.branch),
                    ref.basedir, true);
            KeyStore keyStore = new KeyStore(dir);
            keyStoreList.add(keyStore);
        }
    }

    private void loadUserIdentities() throws IOException, CryptoException {
        List<String> userIdentities = storageDir.listDirectories(PATH_USER_IDS);

        for (String uidPath : userIdentities) {
            UserDataRef ref = readRef(PATH_USER_IDS + "/" + uidPath);
            UserIdentity userIdentity = new UserIdentity();
            SecureStorageDir dir = new SecureStorageDir(environment.getByStorageId(ref.branchUid,
                    ref.branch), ref.basedir, true);
            userIdentity.open(dir, getKeyStoreFinder());
            userIdentityList.add(userIdentity);
        }

        String mainUserIdentityId = storageDir.readSecureString(PATH_MAIN_USER_IDENTITY);
        for (UserIdentity userIdentity : userIdentityList) {
            if (userIdentity.getUid().equals(mainUserIdentityId)) {
                mainUserIdentity = userIdentity;
                break;
            }
        }
    }

    private void loadRemoteStorageLinks() throws IOException, CryptoException {
        String baseDir = PATH_REMOTES;
        List<String> remoteUids = storageDir.listDirectories(baseDir);

        for (String uidPath : remoteUids) {
            SecureStorageDir linkDir = new SecureStorageDir(storageDir, baseDir + "/" + uidPath);
            RemoteStorageLink link = new RemoteStorageLink(environment, linkDir, mainUserIdentity.getMyself());
            remoteStorageLinks.put(link.getLocalStorage(), link);
        }
    }

    private void loadMailboxes() throws IOException, CryptoException {
        String baseDir = PATH_MAILBOXES;
        List<String> mailboxes = storageDir.listDirectories(baseDir);

        for (String mailboxId : mailboxes) {
            UserDataRef ref = readRef(baseDir + "/" + mailboxId);
            SecureStorageDir dir = new SecureStorageDir(environment.getByStorageId(ref.branchUid,
                    ref.branch), ref.basedir, true);
            Mailbox mailbox = new Mailbox(environment, dir, getKeyStoreFinder(), getUserIdentityFinder());

            mailboxList.add(mailbox);
        }

        String mainMailboxId = storageDir.readString(PATH_MAIN_MAILBOX);
        for (Mailbox mailbox : mailboxList) {
            if (mailbox.getUid().equals(mainMailboxId)) {
                mainMailbox = mailbox;
                break;
            }
        }
    }

    private void writeRef(String path, String storageUid, StorageDir refTarget) throws IOException {
        storageDir.writeString(path + PATH_STORAGE_UID, storageUid);
        storageDir.writeString(path + PATH_STORAGE_BRANCH, refTarget.getBranch());
        storageDir.writeString(path + PATH_STORAGE_BASE_DIR, refTarget.getBaseDir());
    }

    private class UserDataRef {
        public String branchUid;
        public String branch;
        public String basedir;
    }

    private UserDataRef readRef(String path) throws IOException {
        UserDataRef ref = new UserDataRef();

        if (!path.equals(""))
            path += "/";
        ref.branchUid = storageDir.readString(path + PATH_STORAGE_UID);
        ref.branch = storageDir.readString(path + PATH_STORAGE_BRANCH);
        ref.basedir = storageDir.readString(path + PATH_STORAGE_BASE_DIR);

        return ref;
    }
}
