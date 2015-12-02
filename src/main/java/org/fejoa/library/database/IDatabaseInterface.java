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
    void init(String path, String branch, boolean create) throws IOException;

    String getPath();
    String getBranch();

    byte[] readBytes(String path) throws IOException;
    void writeBytes(String path, byte[] bytes) throws IOException;

    String commit() throws IOException;

    List<String> listFiles(String path) throws IOException;
    List<String> listDirectories(String path) throws IOException;

    String getTip() throws IOException;
    void updateTip(String commit) throws IOException;

    void merge(String theirCommitId) throws IOException;

    void remove(String path) throws IOException;

    DatabaseDiff getDiff(String baseCommit, String endCommit) throws IOException;

    // sync
    String getLastSyncCommit(String remoteName, String remoteBranch) throws IOException;
    void updateLastSyncCommit(String remoteName, String remoteBranch, String uid) throws IOException;
    byte[] exportPack(String startCommit, String endCommit, String ignoreCommit, int format) throws IOException;
    //! import pack, tries to merge and update the tip
    void importPack(byte pack[], String baseCommit, String endCommit, int format) throws IOException;
}
