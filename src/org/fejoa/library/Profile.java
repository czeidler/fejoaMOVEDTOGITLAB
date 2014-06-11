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
import org.fejoa.library.database.JGitInterface;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private List<KeyStore> keyStoreList = new ArrayList<>();

    private class KeyStoreFinder implements IKeyStoreFinder {
        List<KeyStore> keyStoreList;

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
        addKeyStore(keyStore);

        SecretKey key = crypto.generateSymmetricKey(CryptoSettings.SYMMETRIC_KEY_SIZE);
        KeyId keyId = keyStore.writeSymmetricKey(key, crypto.generateInitializationVector(
                CryptoSettings.SYMMETRIC_KEY_IV_SIZE));
        storageDir.setTo(keyStore, keyId);

        writeUserData(uid, storageDir, keyStore, keyId);

/*
        // create mailbox
        DatabaseBranch *mailboxesBranch = databaseBranchFor(databaseBranch->getDatabasePath(), "mailboxes");
        Mailbox *mailbox = NULL;
        error = createNewMailbox(mailboxesBranch, &mailbox);
        if (error != WP::kOk)
            return error;
        mainMailbox = mailbox->getUid();

        // init user identity
        DatabaseBranch *identitiesBranch = databaseBranchFor(databaseBranch->getDatabasePath(), "identities");
        UserIdentity *identity = NULL;
        error = createNewUserIdentity(identitiesBranch, mailbox, &identity);
        if (error != WP::kOk)
            return error;

        error = writeConfig();
        if (error != WP::kOk)
            return error;

        return WP::kOk;*/
    }

    @Override
    public void commit() throws Exception {
        super.commit();

        for (KeyStore keyStore : keyStoreList)
            keyStore.getStorageDir().getDatabase().commit();
    }

    public boolean open(String password) throws IOException, CryptoException {
        loadKeyStores();

        if (!readUserData(storageDir, getKeyStoreFinder(), password))
            return false;

        return true;
/*
        error = read(kMainMailboxPath, mainMailbox);
        if (error != WP::kOk)
            return error;


        // remotes
        error = loadRemotes();
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

        // load identities
        error = loadUserIdentities();
        if (error != WP::kOk)
            return error;

        return WP::kOk;*/
    }

    public IKeyStoreFinder getKeyStoreFinder() {
        return new KeyStoreFinder(keyStoreList);
    }

    private void addKeyStore(KeyStore keyStore) throws Exception {
        String path = "key_stores/";
        path += keyStore.getUid();
        path += "/";

        writeRef(path, keyStore.getStorageDir());
        keyStoreList.add(keyStore);
    }

    void loadKeyStores() throws IOException {
        List<String> keyStores = storageDir.listDirectories("key_stores");

        for (String keyStorePath : keyStores) {
            UserDataRef ref = readRef("key_stores/" + keyStorePath);
            StorageDir storageDir = new StorageDir(DatabaseBucket.get(ref.path, ref.branch), ref.basedir);
            KeyStore keyStore = new KeyStore(storageDir);
            keyStoreList.add(keyStore);
        }
    }

    private void writeRef(String path, StorageDir refTarget) throws Exception {
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
