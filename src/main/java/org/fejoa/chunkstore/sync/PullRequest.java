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
import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;


public class PullRequest {
    static final public int PULL_REQUEST_VERSION = 1;
    static final public int GET_REMOTE_TIP = 1;
    static final public int GET_CHUNKS = 2;

    final private ChunkStore chunkStore;
    final private Repository requestRepo;

    public PullRequest(ChunkStore chunkStore, Repository requestRepo) {
        this.chunkStore = chunkStore;
        this.requestRepo = requestRepo;
    }

    static public void writeRequestHeader(DataOutputStream outputStream, int request) throws IOException {
        outputStream.writeInt(PULL_REQUEST_VERSION);
        outputStream.writeInt(request);
    }

    static public int receiveRequest(DataInputStream inputStream) throws IOException {
        int version = inputStream.readInt();
        if (version != PULL_REQUEST_VERSION)
            throw new IOException("Version " + PULL_REQUEST_VERSION + " expected but got:" + version);
        return inputStream.readInt();
    }

    static public void receiveHeader(DataInputStream inputStream, int request) throws IOException {
        int response = receiveRequest(inputStream);
        if (response != request)
            throw new IOException("GET_REMOTE_TIP response expected but got: " + response);
    }

    private String getRemoteTip(IRemotePipe remotePipe) throws IOException {
        DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
        writeRequestHeader(outputStream, GET_REMOTE_TIP);
        DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
        receiveHeader(inputStream, GET_REMOTE_TIP);

        String remoteTipString = StreamHelper.readString(inputStream);

        return remoteTipString;
    }

    static public ChunkFetcher createRemotePipeFetcher(ChunkStore.Transaction transaction,
                                                       final IRemotePipe remotePipe) {
        return new ChunkFetcher(transaction, new ChunkFetcher.IFetcherBackend() {
            @Override
            public void fetch(ChunkStore.Transaction transaction, List<HashValue> requestedChunks) throws IOException {
                DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
                PullRequest.writeRequestHeader(outputStream, GET_CHUNKS);
                outputStream.writeInt(requestedChunks.size());
                for (HashValue hashValue : requestedChunks)
                    outputStream.write(hashValue.getBytes());

                DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
                PullRequest.receiveHeader(inputStream, GET_CHUNKS);
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

    public BoxPointer pull(IRemotePipe remotePipe) throws IOException, CryptoException {
        String remoteTipMessage = getRemoteTip(remotePipe);
        if (remoteTipMessage.equals(""))
            return new BoxPointer();
        BoxPointer remoteTip = requestRepo.getCommitCallback().commitPointerFromLog(remoteTipMessage);
        IRepoChunkAccessors.ITransaction transaction = requestRepo.getChunkAccessors().startTransaction();
        GetCommitJob getCommitJob = new GetCommitJob(null, transaction, remoteTip);
        ChunkStore.Transaction chunkStoreTransaction = chunkStore.openTransaction();
        ChunkFetcher chunkFetcher = createRemotePipeFetcher(chunkStoreTransaction, remotePipe);
        chunkFetcher.enqueueJob(getCommitJob);
        chunkFetcher.fetch();

        requestRepo.merge(chunkStoreTransaction, getCommitJob.getCommitBox());
        return remoteTip;
    }
}
