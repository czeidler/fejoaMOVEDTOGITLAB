/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.IRemotePipe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.fejoa.chunkstore.sync.Request.GET_CHUNKS;


public class PullRequest {
    final private ChunkStore chunkStore;
    final private Repository requestRepo;

    public PullRequest(ChunkStore chunkStore, Repository requestRepo) {
        this.chunkStore = chunkStore;
        this.requestRepo = requestRepo;
    }

    static public ChunkFetcher createRemotePipeFetcher(ChunkStore.Transaction transaction,
                                                       final IRemotePipe remotePipe) {
        return new ChunkFetcher(transaction, new ChunkFetcher.IFetcherBackend() {
            @Override
            public void fetch(ChunkStore.Transaction transaction, List<HashValue> requestedChunks) throws IOException {
                DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
                Request.writeRequestHeader(outputStream, GET_CHUNKS);
                outputStream.writeInt(requestedChunks.size());
                for (HashValue hashValue : requestedChunks)
                    outputStream.write(hashValue.getBytes());

                DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
                Request.receiveHeader(inputStream, GET_CHUNKS);
                int chunkCount = inputStream.readInt();
                if (chunkCount != requestedChunks.size()) {
                    throw new IOException("Received chunk count is: " + chunkCount + " but " + requestedChunks.size()
                            + " expected.");
                }

                for (int i = 0; i < chunkCount; i++) {
                    HashValue hashValue = new HashValue(HashValue.HASH_SIZE);
                    inputStream.readFully(hashValue.getBytes());
                    int size = inputStream.readInt();
                    byte[] buffer = new byte[size];
                    inputStream.readFully(buffer);
                    PutResult<HashValue> result = transaction.put(buffer);
                    if (!result.key.equals(hashValue))
                        throw new IOException("Hash miss match.");
                }
            }
        });
    }

    public BoxPointer pull(IRemotePipe remotePipe, String branch) throws IOException, CryptoException {
        String remoteTipMessage = LogEntryRequest.getRemoteTip(remotePipe, branch).getMessage();
        if (remoteTipMessage.equals(""))
            return new BoxPointer();
        BoxPointer remoteTip = requestRepo.getCommitCallback().commitPointerFromLog(remoteTipMessage);
        IRepoChunkAccessors.ITransaction transaction = requestRepo.getChunkAccessors().startTransaction();
        GetCommitJob getCommitJob = new GetCommitJob(null, transaction, remoteTip);
        ChunkFetcher chunkFetcher = createRemotePipeFetcher(transaction.getRawAccessor(), remotePipe);
        chunkFetcher.enqueueJob(getCommitJob);
        chunkFetcher.fetch();

        requestRepo.merge(transaction, getCommitJob.getCommitBox());
        return remoteTip;
    }
}
