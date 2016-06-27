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


public class Storage implements IStorageDirBundle {
    private String id;

    public Storage(String id) {
        this.id = id;
    }

    public Storage() {
    }

    public String getId() {
        return id;
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
