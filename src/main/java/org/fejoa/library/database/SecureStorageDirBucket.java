/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class SecureStorageDirBucket {
    private List<SecureStorageDir> secureStorageDirs = new ArrayList<>();
    static private SecureStorageDirBucket instance;

    private SecureStorageDirBucket() {

    }

    static public SecureStorageDir getDefault(String branch) throws IOException {
        return get(".git", branch);
    }

    static public SecureStorageDir getChannelBranchStorage(String branch) throws IOException {
        return getDefault(branch);
    }

    static public SecureStorageDir getByStorageId(String storageUid, String branch) throws IOException {
        // ignore branch id for now
        return getDefault(branch);
    }

    static public SecureStorageDir get(String path, String branch) throws IOException {
        if (instance == null)
            instance = new SecureStorageDirBucket();
        return instance.getPrivate(path, branch);
    }

    private SecureStorageDir getPrivate(String path, String branch) throws IOException {
        for (SecureStorageDir dir : secureStorageDirs) {
            if (dir.getPath().equals(path) && dir.getBranch().equals(branch))
                return dir;
        }
        // not found create one
        JGitInterface database = new JGitInterface();
        database.init(path, branch, true);

        SecureStorageDir secureStorageDir = new SecureStorageDir(database, "");
        secureStorageDirs.add(secureStorageDir);
        return secureStorageDir;
    }
}