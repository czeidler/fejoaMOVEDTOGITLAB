/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.*;
import org.fejoa.library.database.IDatabaseInterface;
import org.fejoa.library.remote.IRemoteRequest;
import org.fejoa.library.remote.RemoteConnection;
import org.fejoa.library.remote.RemoteRequestFactory;
import org.fejoa.library.remote.RemoteStorageLink;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Profile
refs
-- mailboxes
-- keystores
-- contacts

Mailbox
--encryptkeyid

MailboxBranches
*/


interface IKeyStoreFinder {
    public KeyStore find(String keyStoreId);
}

interface IPublicContactFinder {
    public ContactPublic find(String id);
}

public class Profile extends UserData {
    final private List<KeyStore> keyStoreList = new ArrayList<>();
    final private List<UserIdentity> userIdentityList = new ArrayList<>();
    final private Map<IDatabaseInterface, RemoteStorageLink> remoteStorageLinks = new HashMap<>();
    private UserIdentity mainUserIdentity = null;

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

    public Profile(IDatabaseInterface database, String baseDir) {
        storageDir = new SecureStorageDir(database, baseDir);
    }

    public void createNew(String password) throws Exception {
        ICryptoInterface crypto = Crypto.get();
        uid = CryptoHelper.toHex(crypto.generateInitializationVector(20));

        // init key store and master key
        KeyStore keyStore = new KeyStore(password);
        IDatabaseInterface keyStoreDatabase = DatabaseBucket.get(storageDir.getDatabase().getPath(), "key_stores");
        keyStore.create(new StorageDir(keyStoreDatabase, keyStore.getUid()));
        addAndWriteKeyStore(keyStore);

        SecretKey key = crypto.generateSymmetricKey(CryptoSettings.SYMMETRIC_KEY_SIZE);
        KeyId keyId = keyStore.writeSymmetricKey(key, crypto.generateInitializationVector(
                CryptoSettings.SYMMETRIC_KEY_IV_SIZE));
        storageDir.setTo(keyStore, keyId);

        UserIdentity userIdentity = new UserIdentity();
        IDatabaseInterface userIdDatabase = DatabaseBucket.get(storageDir.getDatabase().getPath(), "user_identities");
        userIdentity.createNew(userIdDatabase, "", keyStore, keyId);
        addAndWriteUseIdentity(userIdentity);
        mainUserIdentity = userIdentity;
        storageDir.writeSecureString("main_user_identity", mainUserIdentity.getUid());

        addAndWriteRemoteStorageLink(keyStoreDatabase, userIdentity.getMyself());
        addAndWriteRemoteStorageLink(userIdDatabase, userIdentity.getMyself());

        writeUserData(uid, storageDir, keyStore, keyId);

/*
        // create mailbox
        DatabaseBranch *mailboxesBranch = databaseBranchFor(databaseBranch->getDatabasePath(), "mailboxes");
        Mailbox *mailbox = NULL;
        error = createNewMailbox(mailboxesBranch, &mailbox);
        if (error != WP::kOk)
            return error;
        mainMailbox = mailbox->getUid();

        return WP::kOk;*/
    }

    @Override
    public void commit() throws Exception {
        super.commit();

        for (KeyStore keyStore : keyStoreList)
            keyStore.getStorageDir().getDatabase().commit();

        for (UserIdentity entry : userIdentityList)
            entry.getStorageDir().getDatabase().commit();
    }

    public void setEmptyRemotes(String url, String serverUser) {
        IRemoteRequest remoteRequest = RemoteRequestFactory.getRemoteRequest(url);
        for (RemoteStorageLink link : remoteStorageLinks.values()) {
            if (link.getRemoteConnection() == null)
                continue;
            link.setTo(new RemoteConnection(remoteRequest), serverUser);
        }
    }

    public boolean open(String password) throws IOException, CryptoException {
        loadKeyStores();

        if (!readUserData(storageDir, getKeyStoreFinder(), password))
            return false;

        loadUserIdentities();

        String mainUserIdentityId = storageDir.readSecureString("main_user_identity");
        for (UserIdentity userIdentity : userIdentityList) {
            if (userIdentity.getUid().equals(mainUserIdentityId)) {
                mainUserIdentity = userIdentity;
                break;
            }
        }

        // must be called after mainUserIdentity has been found
        loadRemoteStorageLinks();
        return true;
/*
        error = read(kMainMailboxPath, mainMailbox);
        if (error != WP::kOk)
            return error;

        // load database branches
        error = loadDatabaseBranches();
        if (error != WP::kOk)
            return error;

        // load mailboxes
        error = loadMailboxes();
        if (error != WP::kOk)
            return error;

        return WP::kOk;*/
    }

    public IKeyStoreFinder getKeyStoreFinder() {
        return new KeyStoreFinder(keyStoreList);
    }

    public List<UserIdentity> getUserIdentityList() {
        return userIdentityList;
    }

    private void addAndWriteKeyStore(KeyStore keyStore) throws IOException {
        String path = "key_stores/";
        path += keyStore.getUid();
        path += "/";

        writeRef(path, keyStore.getStorageDir());
        keyStoreList.add(keyStore);
    }

    private void addAndWriteUseIdentity(UserIdentity userIdentity) throws IOException {
        String path = "user_ids/";
        path += userIdentity.getUid();
        path += "/";

        writeRef(path, userIdentity.getStorageDir());
        userIdentityList.add(userIdentity);
    }

    private void addAndWriteRemoteStorageLink(IDatabaseInterface databaseInterface, ContactPrivate myself)
            throws IOException, CryptoException {
        if (remoteStorageLinks.containsKey(databaseInterface))
            return;
        RemoteStorageLink remoteStorageLink = new RemoteStorageLink(databaseInterface, myself);
        String path = "remotes/" + remoteStorageLink.getUid();
        remoteStorageLink.write(new SecureStorageDir(storageDir, path));
        remoteStorageLinks.put(databaseInterface, remoteStorageLink);
    }

    private void loadKeyStores() throws IOException {
        List<String> keyStores = storageDir.listDirectories("key_stores");

        for (String keyStorePath : keyStores) {
            UserDataRef ref = readRef("key_stores/" + keyStorePath);
            StorageDir storageDir = new StorageDir(DatabaseBucket.get(ref.path, ref.branch), ref.basedir);
            KeyStore keyStore = new KeyStore(storageDir);
            keyStoreList.add(keyStore);
        }
    }

    private void loadUserIdentities() throws IOException, CryptoException {
        List<String> userIdentities = storageDir.listDirectories("user_ids");

        for (String uidPath : userIdentities) {
            UserDataRef ref = readRef("user_ids/" + uidPath);
            UserIdentity userIdentity = new UserIdentity();
            userIdentity.open(DatabaseBucket.get(ref.path, ref.branch), ref.basedir, getKeyStoreFinder());
            userIdentityList.add(userIdentity);
        }
    }

    private void loadRemoteStorageLinks() throws IOException, CryptoException {
        String baseDir = "remotes";
        List<String> remoteUids = storageDir.listDirectories(baseDir);

        for (String uidPath : remoteUids) {
            SecureStorageDir linkDir = new SecureStorageDir(storageDir, baseDir + "/" + uidPath);
            RemoteStorageLink link = new RemoteStorageLink(linkDir, mainUserIdentity.getMyself());
            remoteStorageLinks.put(link.getDatabaseInterface(), link);
        }
    }

    private void writeRef(String path, StorageDir refTarget) throws IOException {
        IDatabaseInterface databaseInterface = refTarget.getDatabase();
        storageDir.writeString(path + "database_path", databaseInterface.getPath());
        storageDir.writeString(path + "database_branch", databaseInterface.getBranch());
        storageDir.writeString(path + "database_base_dir", refTarget.getBaseDir());
    }

    private class UserDataRef {
        public String path;
        public String branch;
        public String basedir;
    }

    private UserDataRef readRef(String path) throws IOException {
        UserDataRef ref = new UserDataRef();

        IDatabaseInterface databaseInterface = storageDir.getDatabase();
        if (!path.equals(""))
            path += "/";
        ref.path = storageDir.readString(path + "database_path");
        ref.branch = storageDir.readString(path + "database_branch");
        ref.basedir = storageDir.readString(path + "database_base_dir");

        return ref;
    }
}
