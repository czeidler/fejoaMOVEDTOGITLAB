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
import java.util.List;


public class ContactStore extends StorageKeyStore {
    final private StorageDirList<ContactPublic> contactList;

    static public ContactStore create(FejoaContext context, String id, KeyStore keyStore, KeyId keyId)
            throws IOException, CryptoException {
        StorageDir dir = context.getStorage(id);
        ContactStore storage = new ContactStore(context, dir);
        storage.create(keyStore, keyId);
        return storage;
    }

    static public ContactStore open(FejoaContext context, StorageDir dir, List<KeyStore> keyStores) throws IOException,
            CryptoException {
        ContactStore storage = new ContactStore(context, dir);
        storage.open(keyStores);
        return storage;
    }

    protected ContactStore(final FejoaContext context, StorageDir dir) {
        super(context, dir);

        contactList = new StorageDirList<>(
                new StorageDirList.IEntryIO<ContactPublic>() {
                    @Override
                    public String getId(ContactPublic entry) {
                        return entry.getId();
                    }

                    @Override
                    public ContactPublic read(StorageDir dir) throws IOException {
                        return new ContactPublic(context, dir);
                    }

                    @Override
                    public void write(ContactPublic entry, StorageDir dir) throws IOException {
                        entry.setStorageDir(dir);
                    }
                });
    }

    @Override
    protected void create(KeyStore keyStore, KeyId keyId) throws IOException, CryptoException {
        super.create(keyStore, keyId);

    }

    @Override
    protected void open(List<KeyStore> keyStores) throws IOException, CryptoException {
        super.open(keyStores);
    }

    public ContactPublic addConntact(String id) throws IOException {
        ContactPublic contactPublic = new ContactPublic(context);
        contactList.add(contactPublic);
        contactPublic.setId(id);
        return contactPublic;
    }
}
