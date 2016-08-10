/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.Config;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.remote.IRemotePipe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.fejoa.chunkstore.sync.Request.HAS_CHUNKS;


public class HasChunksRequest {
    static public List<HashValue> hasChunks(IRemotePipe remotePipe, List<HashValue> chunks) throws IOException {
        DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
        Request.writeRequestHeader(outputStream, HAS_CHUNKS);

        outputStream.writeInt(chunks.size());
        for (HashValue hashValue : chunks)
            outputStream.write(hashValue.getBytes());

        // reply
        DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
        Request.receiveHeader(inputStream, HAS_CHUNKS);
        int nChunks = inputStream.readInt();
        final List<HashValue> hasChunks = new ArrayList<>();
        for (int i = 0; i < nChunks; i++) {
            HashValue hasChunk = Config.newBoxHash();
            inputStream.readFully(hasChunk.getBytes());
            hasChunks.add(hasChunk);
        }
        return hasChunks;
    }
}
