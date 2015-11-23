/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library2.database.StorageDir;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class FejoaContext {
    final static private String INFO_FILE = "info";

    final private String homeDir;
    private CryptoSettings cryptoSettings = CryptoSettings.getDefault();

    private List<StorageDir> secureStorageDirs = new ArrayList<>();

    public FejoaContext(String homeDir) {
        this.homeDir = homeDir;
    }

    public String getHomeDir() {
        return homeDir;
    }

    public StorageDir getStorage(String branch) throws IOException {
        return get(".git", branch);
    }

    public ICryptoInterface getCrypto() {
        return Crypto.get();
    }

    public CryptoSettings getCryptoSettings() {
        return cryptoSettings;
    }

    public StorageDir get(String path, String branch) throws IOException {
        path = StorageDir.appendDir(homeDir, path);
        for (StorageDir dir : secureStorageDirs) {
            if (dir.getDatabasePath().equals(path) && dir.getBranch().equals(branch))
                return new StorageDir(dir);
        }
        // not found create one
        JGitInterface database = new JGitInterface();
        database.init(path, branch, true);

        StorageDir storageDir = new StorageDir(database, "");
        secureStorageDirs.add(storageDir);
        return new StorageDir(storageDir);
    }

    public void setUserDataId(String id) throws IOException {
        File file = new File(homeDir, INFO_FILE);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
        writer.write(id);
    }

    public String getUserDataId() throws IOException {
        File file = new File(homeDir, INFO_FILE);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        return bufferedReader.readLine();
    }
}
