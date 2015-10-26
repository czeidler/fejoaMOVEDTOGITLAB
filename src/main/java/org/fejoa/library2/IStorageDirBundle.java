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


public interface IStorageDirBundle {
    void write(StorageDir dir) throws IOException;
    void read(StorageDir dir) throws IOException;
}
