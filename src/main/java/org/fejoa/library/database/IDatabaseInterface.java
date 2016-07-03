/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.chunkstore.HashValue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;


public interface IDatabaseInterface {
    void init(String path, String branch, boolean create) throws IOException;

    String getPath();
    String getBranch();

    InputStream read(String path) throws IOException;
    void write(String path, long length, InputStream stream) throws IOException;

    HashValue getHash(String path) throws IOException;
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
}
