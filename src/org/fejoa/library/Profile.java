/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
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

class DatabaseCache {
    private List<IDatabaseInterface> databaseList = new ArrayList<>();

    public IDatabaseInterface get(String path, String branch) throws IOException {
        for (IDatabaseInterface database : databaseList) {
            if (database.getPath().equals(path) && database.getBranch().equals(branch))
                return database;
        }
        // not found create one
        JGitInterface database = new JGitInterface();
        database.init(path, branch, true);

        databaseList.add(database);
        return database;
    }
}


interface IKeyStoreFinder {
    public KeyStore find(String keyStoreId);
}

interface IPublicContactFinder {
    public ContactPublic find(String id);
}

public class Profile {
    private String uid;
    private DatabaseCache databases = new DatabaseCache();
    private IDatabaseInterface database = null;
    private KeyStore keyStore = null;
    private SecureStorageDir storageDir;

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

    public Profile(IDatabaseInterface database) {
        this.database = database;
    }

    public void createNew(String password) throws Exception {
        ICryptoInterface crypto = Crypto.get();
        uid = CryptoHelper.toHex(crypto.generateInitializationVector(20));

        // init key store and master key
        keyStore = new KeyStore(password);
        IDatabaseInterface keyStoreDatabase = databases.get(database.getPath(), "key_stores");
        keyStore.create(new StorageDir(keyStoreDatabase, keyStore.getUid()));
        addKeyStore(keyStore);

        SecretKey key = crypto.generateSymmetricKey(CryptoSettings.SYMMETRIC_KEY_SIZE);
        KeyId keyId = keyStore.writeSymmetricKey(key, crypto.generateInitializationVector(
                CryptoSettings.SYMMETRIC_KEY_IV_SIZE));
        storageDir = new SecureStorageDir(database, "", keyStore, keyId);
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

    boolean open(String password) throws Exception {
        loadKeyStores(password);

        return false;
/*
        error = read(kMainMailboxPath, mainMailbox);
        if (error != WP::kOk)
            return error;

        error = EncryptedUserData::open(&keyStoreFinder);
        if (error != WP::kOk)
            return error;
        error = keyStore->open(password);
        if (error != WP::kOk)
            return error;

        storageDir =

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

    private void addKeyStore(KeyStore keyStore) throws Exception {
        String path = "key_stores/";
        path += keyStore.getUid();

        writeRef(path, keyStore.getStorageDir());
        keyStoreList.add(keyStore);
    }

    void loadKeyStores(String password) throws Exception {
        List<String> keyStores = storageDir.listDirectories("key_stores");

        for (String keyStorePath : keyStores) {
            UserDataRef ref = readRef("key_stores/" + keyStorePath);
            StorageDir storageDir = new StorageDir(databases.get(ref.path, ref.branch), ref.basedir);
            KeyStore keyStore = new KeyStore(storageDir);
            if (!keyStore.open(password))
                continue;
            keyStoreList.add(keyStore);
        }
    }

    private void writeRef(String path, StorageDir storageDir) throws Exception {
        IDatabaseInterface databaseInterface = storageDir.getDatabase();
        storageDir.writeString("database_path", databaseInterface.getPath());
        storageDir.writeString("database_branch", databaseInterface.getBranch());
        storageDir.writeString("database_base_dir", storageDir.getBaseDir());
    }

    private class UserDataRef {
        public String path;
        public String branch;
        public String basedir;
    }

    private UserDataRef readRef(String path) throws Exception {
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
