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


public class StorageList extends AbstractStorageDirList<IStorageDirBundle> {
    static public class StorageEntry implements IStorageDirBundle {
        static final private String STORAGE_ID_KEY = "id";

        private String id;

        public StorageEntry(String id) {
            this.id = id;
        }

        public StorageEntry() {
        }

        @Override
        public void write(StorageDir dir) throws IOException {
            dir.writeString(STORAGE_ID_KEY, id);
        }

        @Override
        public void read(StorageDir dir) throws IOException {
            id = dir.readString(STORAGE_ID_KEY);
        }
    }

    static public StorageList create(StorageDir dir) {
        return new StorageList(dir);
    }

    static public StorageList load(StorageDir dir) {
        StorageList storageList = new StorageList(dir);
        storageList.load();
        return storageList;
    }

    @Override
    protected StorageEntry instantiate(StorageDir dir) {
        return new StorageEntry();
    }

    private StorageList(StorageDir storageDir) {
        super(storageDir);
    }
}
