/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;


import org.fejoa.library2.database.StorageDir;

import java.io.IOException;


public class RemoteList extends StorageDirList<Remote> {
    public RemoteList(StorageDir storageDir) {
        super(storageDir, new AbstractEntryIO<Remote>() {
            @Override
            public String getId(Remote entry) {
                return entry.getId();
            }

            @Override
            public Remote read(StorageDir dir) throws IOException {
                Remote remote = new Remote();
                remote.read(dir);
                return remote;
            }
        });
    }
}
