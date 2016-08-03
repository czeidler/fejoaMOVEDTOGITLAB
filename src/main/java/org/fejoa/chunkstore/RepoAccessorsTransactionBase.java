/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.IOException;


abstract public class RepoAccessorsTransactionBase implements IRepoChunkAccessors.ITransaction {
    final protected ChunkStore.Transaction transaction;

    public RepoAccessorsTransactionBase(ChunkStore chunkStore) throws IOException {
        this.transaction = chunkStore.openTransaction();
    }

    @Override
    public void finishTransaction() throws IOException {
        transaction.commit();
    }
}
