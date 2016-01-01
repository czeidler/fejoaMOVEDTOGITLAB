/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.util.List;


public class AccessStore extends StorageKeyStore {
    private StorageDirList<AccessToken> accessTokens;

    static public AccessStore create(FejoaContext context, String id, KeyStore keyStore, KeyId keyId)
            throws IOException, CryptoException {
        StorageDir dir = context.getStorage(id);
        AccessStore storage = new AccessStore(context, dir);
        storage.create(keyStore, keyId);
        storage.init();
        return storage;
    }

    static public AccessStore open(FejoaContext context, StorageDir dir, List<KeyStore> keyStores) throws IOException,
            CryptoException {
        AccessStore storage = new AccessStore(context, dir);
        storage.open(keyStores);
        storage.init();
        return storage;
    }

    protected AccessStore(final FejoaContext context, StorageDir dir) {
        super(context, dir);
    }

    private void init() {
        accessTokens = new StorageDirList<>(storageDir,
                new StorageDirList.AbstractEntryIO<AccessToken>() {
                    @Override
                    public String getId(AccessToken entry) {
                        return entry.getId();
                    }

                    @Override
                    public AccessToken read(StorageDir dir) throws IOException {
                        return AccessToken.open(context, dir);
                    }
                });
    }

    public void addAccessToken(AccessToken token) throws IOException {
        accessTokens.add(token);
    }
}
