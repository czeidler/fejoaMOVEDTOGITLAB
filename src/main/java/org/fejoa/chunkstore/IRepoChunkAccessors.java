/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.IOException;


public interface IRepoChunkAccessors {
    /**
     * Accessor to access commit chunks.
     */
    IChunkAccessor getCommitAccessor();
    /**
     * Accessor to access the directory structure chunks.
     */
    IChunkAccessor getTreeAccessor();
    /**
     * Accessor to access the files structure chunks.
     */
    IChunkAccessor getFileAccessor(String filePath);

    void startTransaction() throws IOException;

    void finishTransaction() throws IOException;
}
