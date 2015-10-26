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


public class FejoaEnvironment {
    final String homeDir;

    private List<SecureStorageDir> secureStorageDirs = new ArrayList<>();

    public FejoaEnvironment(String homeDir) {
        this.homeDir = homeDir;
    }

    public String getHomeDir() {
        return homeDir;
    }

    public SecureStorageDir getDefault(String branch) throws IOException {
        return get(".git", branch);
    }

    public SecureStorageDir getChannelBranchStorage(String branch) throws IOException {
        return getDefault(branch);
    }

    public SecureStorageDir getByStorageId(String storageUid, String branch) throws IOException {
        // ignore branch id for now
        return getDefault(branch);
    }

    public SecureStorageDir get(String path, String branch) throws IOException {
        path = StorageDir.appendDir(homeDir, path);
        for (SecureStorageDir dir : secureStorageDirs) {
            if (dir.getDatabasePath().equals(path) && dir.getBranch().equals(branch))
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
