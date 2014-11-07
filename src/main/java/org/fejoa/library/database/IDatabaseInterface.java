/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java.io.IOException;
import java.util.List;


public interface IDatabaseInterface {
    public void init(String path, String branch, boolean create) throws IOException;

    public String getPath();
    public String getBranch();

    public byte[] readBytes(String path) throws IOException;
    public void writeBytes(String path, byte[] bytes) throws IOException;

    public String commit() throws IOException;

    public List<String> listFiles(String path) throws IOException;
    public List<String> listDirectories(String path) throws IOException;

    public String getTip() throws IOException;
    public void updateTip(String commit) throws IOException;

    public void remove(String path) throws IOException;

    public DatabaseDiff getDiff(String baseCommit, String endCommit) throws IOException;

    // sync
    public String getLastSyncCommit(String remoteName, String remoteBranch) throws IOException;
    public void updateLastSyncCommit(String remoteName, String remoteBranch, String uid) throws IOException;
    public byte[] exportPack(String startCommit, String endCommit, String ignoreCommit, int format) throws IOException;
    //! import pack, tries to merge and update the tip
    public void importPack(byte pack[], String baseCommit, String endCommit, int format) throws IOException;
}
