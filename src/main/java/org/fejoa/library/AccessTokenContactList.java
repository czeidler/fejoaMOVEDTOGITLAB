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


/**
 * A list of granted access from a contact.
 */
public class AccessTokenContactList extends StorageDirList<AccessTokenContact> {
    static final private String ACCESS_TOKEN_KEY = "accessToken";

    public AccessTokenContactList(final FejoaContext context, StorageDir storageDir) {
        super(storageDir, new IEntryIO<AccessTokenContact>() {
            @Override
            public String getId(AccessTokenContact entry) {
                return entry.getId();
            }

            @Override
            public AccessTokenContact read(StorageDir dir) throws IOException {
                try {
                    return new AccessTokenContact(context, dir.readString(ACCESS_TOKEN_KEY));
                } catch (Exception e) {
                    throw new IOException(e.getMessage());
                }
            }

            @Override
            public void write(AccessTokenContact entry, StorageDir dir) throws IOException {
                dir.writeString(ACCESS_TOKEN_KEY, entry.getRawAccessToken());
            }
        });
    }
}
