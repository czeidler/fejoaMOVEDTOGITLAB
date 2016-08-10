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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.fejoa.chunkstore.sync.Request.GET_CHUNKS;


public class PullHandler {
    static public void handleGetChunks(ChunkStore chunkStore, IRemotePipe pipe, DataInputStream inputStream)
            throws IOException {
        int nRequestedChunks = inputStream.readInt();
        List<HashValue> requestedChunks = new ArrayList<>();
        for (int i = 0; i < nRequestedChunks; i++) {
            HashValue hashValue = Config.newBoxHash();
            inputStream.readFully(hashValue.getBytes());
            requestedChunks.add(hashValue);
        }

        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());
        Request.writeRequestHeader(outputStream, GET_CHUNKS);

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
