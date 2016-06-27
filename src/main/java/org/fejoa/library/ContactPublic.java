/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.StorageDir;

import java.io.IOException;
import java.security.PublicKey;


public class ContactPublic extends Contact<PublicKeyItem> {
    final static private String REMOTES_DIR = "remotes";
    final static private String ACCESS_DIR = "access";

    private RemoteList remotes;
    private AccessTokenContactList access;

    /**
     * Should not be called directly but by the contact store.
     *
     * @param context the used context.
     */
    ContactPublic(FejoaContext context) {
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
        access = new AccessTokenContactList(context, new StorageDir(storageDir, ACCESS_DIR));
    }

    @Override
    public PublicKey getVerificationKey(KeyId keyId) {
        return signatureKeys.get(keyId.toString()).getKey();
    }

    public RemoteList getRemotes() {
        return remotes;
    }

    public AccessTokenContactList getAccessTokenList() {
        return access;
    }
}
