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


public class StorageRefList extends AbstractStorageDirList<StorageRefList.StorageEntry> {
    static public class StorageEntry implements IStorageDirBundle {
        private String id;

        public StorageEntry(String id) {
            this.id = id;
        }

        public StorageEntry() {
        }

        @Override
        public void write(StorageDir dir) throws IOException {
            dir.writeString(Constants.ID_KEY, id);
        }

        @Override
        public void read(StorageDir dir) throws IOException {
            id = dir.readString(Constants.ID_KEY);
        }
    }

    static public StorageRefList create(StorageDir dir) {
        return new StorageRefList(dir);
    }

    static public StorageRefList load(StorageDir dir) {
        StorageRefList storageRefList = new StorageRefList(dir);
        storageRefList.load();
        return storageRefList;
    }

    @Override
    protected StorageEntry instantiate(StorageDir dir) {
        return new StorageEntry();
    }

    private StorageRefList(StorageDir storageDir) {
        super(storageDir);
    }
}
