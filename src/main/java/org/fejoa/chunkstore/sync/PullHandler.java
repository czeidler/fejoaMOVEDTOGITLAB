/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.*;
import org.fejoa.library.remote.IRemotePipe;
import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.fejoa.chunkstore.sync.PullRequest.GET_CHUNKS;
import static org.fejoa.chunkstore.sync.PullRequest.GET_REMOTE_TIP;


public class PullHandler implements IHandler {
    final private ChunkStore chunkStore;
    final private ChunkStoreBranchLog localBranchLog;

    public PullHandler(ChunkStore chunkStore, ChunkStoreBranchLog localBranchLog) {
        this.chunkStore = chunkStore;
        this.localBranchLog = localBranchLog;
    }

    public void handle(IRemotePipe pipe) throws IOException {
        DataInputStream inputStream = new DataInputStream(pipe.getInputStream());
        int request = PullRequest.receiveRequest(inputStream);
        switch (request) {
            case GET_REMOTE_TIP:
                handleGetRemoteTip(pipe);
                break;
            case GET_CHUNKS:
                handleGetChunks(pipe, inputStream);
                break;
            default:
                throw new IOException("Unknown request: " + request);
        }
    }

    private void handleGetRemoteTip(IRemotePipe pipe) throws IOException {
        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());
        PullRequest.writeRequestHeader(outputStream, GET_REMOTE_TIP);
        String tip;
        if (localBranchLog.getLatest() == null)
            tip = "";
        else
            tip = localBranchLog.getLatest().getMessage();
        StreamHelper.writeString(outputStream, tip);
    }

    private void handleGetChunks(IRemotePipe pipe, DataInputStream inputStream) throws IOException {
        int nRequestedChunks = inputStream.readInt();
        List<HashValue> requestedChunks = new ArrayList<>();
        for (int i = 0; i < nRequestedChunks; i++) {
            HashValue hashValue = Config.newBoxHash();
            inputStream.readFully(hashValue.getBytes());
            requestedChunks.add(hashValue);
        }

        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());
        PullRequest.writeRequestHeader(outputStream, GET_CHUNKS);

        outputStream.writeInt(requestedChunks.size());
        //TODO: check if we have all chunks before start sending them
        for (HashValue hashValue : requestedChunks) {
            outputStream.write(hashValue.getBytes());
            byte[] chunk = chunkStore.getChunk(hashValue);
            outputStream.writeInt(chunk.length);
            outputStream.write(chunk);
        }
    }
}
