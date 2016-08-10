/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.ChunkStore;
import org.fejoa.chunkstore.ChunkStoreBranchLog;
import org.fejoa.library.remote.IRemotePipe;
import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.fejoa.chunkstore.sync.Request.*;
import static org.fejoa.library.support.StreamHelper.readString;


public class RequestHandler implements IHandler {
    public interface IBranchLogGetter {
        ChunkStoreBranchLog get(String branch) throws IOException;
    }

    final private ChunkStore chunkStore;
    final private IBranchLogGetter logGetter;

    public RequestHandler(ChunkStore chunkStore, IBranchLogGetter logGetter) {
        this.chunkStore = chunkStore;
        this.logGetter = logGetter;
    }

    @Override
    public void handle(IRemotePipe pipe) {
        try {
            DataInputStream inputStream = new DataInputStream(pipe.getInputStream());
            int request = Request.receiveRequest(inputStream);
            switch (request) {
                case GET_REMOTE_TIP:
                    handleGetRemoteTip(pipe, inputStream);
                    break;
                case GET_CHUNKS:
                    PullHandler.handleGetChunks(chunkStore, pipe, inputStream);
                    break;
                case PUT_CHUNKS:
                    PushHandler.handlePutChunks(chunkStore, logGetter, pipe, inputStream);
                    break;
                case HAS_CHUNKS:
                    HasChunksHandler.handleHasChunks(chunkStore, pipe, inputStream);
                    break;
                default:
                    makeError(new DataOutputStream(pipe.getOutputStream()), "Unknown request: " + request);
            }
        } catch (IOException e) {
            try {
                makeError(new DataOutputStream(pipe.getOutputStream()), "Internal error.");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    static public void makeError(DataOutputStream outputStream, String message) throws IOException {
        Request.writeRequestHeader(outputStream, GET_REMOTE_TIP);
        StreamHelper.writeString(outputStream, message);
    }

    public void handleGetRemoteTip(IRemotePipe pipe, DataInputStream inputStream) throws IOException {
        String branch = readString(inputStream);

        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());

        ChunkStoreBranchLog localBranchLog = logGetter.get(branch);
        if (localBranchLog == null) {
            makeError(outputStream, "No access to branch: " + branch);
            return;
        }
        Request.writeRequestHeader(outputStream, GET_REMOTE_TIP);
        String tip;
        if (localBranchLog.getLatest() == null)
            tip = "";
        else
            tip = localBranchLog.getLatest().getMessage();
        StreamHelper.writeString(outputStream, tip);
    }
}
