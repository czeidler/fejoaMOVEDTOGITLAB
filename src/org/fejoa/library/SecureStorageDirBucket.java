/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.IDatabaseInterface;
import org.fejoa.library.database.JGitInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class SecureStorageDirBucket {
    private List<SecureStorageDir> secureStorageDirs = new ArrayList<>();
    static private SecureStorageDirBucket instance;

    private SecureStorageDirBucket() {

    }

    static private void init() {
        instance = new SecureStorageDirBucket();
    }

    static public SecureStorageDir get(String path, String branch) throws IOException {
        if (instance == null)
            init();
        return instance.getPrivate(path, branch);
    }

    private SecureStorageDir getPrivate(String path, String branch) throws IOException {
        for (SecureStorageDir dir : secureStorageDirs) {
            if (dir.getDatabase().getPath().equals(path) && dir.getDatabase().getBranch().equals(branch))
                return dir;
        }
        // not found create one
        JGitInterface database = new JGitInterface();
        database.init(path, branch, true);

        SecureStorageDir secureStorageDir = new SecureStorageDir(database, branch);
        secureStorageDirs.add(secureStorageDir);
        return secureStorageDir;
    }
}
