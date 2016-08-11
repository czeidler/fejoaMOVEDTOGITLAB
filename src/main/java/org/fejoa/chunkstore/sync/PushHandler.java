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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.fejoa.chunkstore.sync.Request.GET_CHUNKS;
import static org.fejoa.chunkstore.sync.Request.PUT_CHUNKS;


public class PushHandler {
    public static void handlePutChunks(ChunkStore chunkStore, RequestHandler.IBranchLogGetter logGetter,
                                       IRemotePipe pipe, DataInputStream inputStream) throws IOException {
        String branch = StreamHelper.readString(inputStream);
        ChunkStoreBranchLog branchLog = logGetter.get(branch);
        if (branchLog == null) {
            RequestHandler.makeError(new DataOutputStream(pipe.getOutputStream()), "No access to branch: " + branch);
            return;
        }
        ChunkStore.Transaction transaction = chunkStore.openTransaction();
        final int rev = inputStream.readInt();
        final String logMessage = StreamHelper.readString(inputStream);
        final int nChunks = inputStream.readInt();
        final List<HashValue> added = new ArrayList<>();
        for (int i = 0; i < nChunks; i++) {
            HashValue chunkHash = Config.newBoxHash();
            inputStream.readFully(chunkHash.getBytes());
            int chunkSize = inputStream.readInt();
            byte[] buffer = new byte[chunkSize];
            inputStream.readFully(buffer);
            PutResult<HashValue> result = transaction.put(buffer);
            if (!result.key.equals(chunkHash))
                throw new IOException("Hash miss match.");
            added.add(chunkHash);
        }

        transaction.commit();
        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());

        try {
            branchLog.lock();
            ChunkStoreBranchLog.Entry latest = branchLog.getLatest();
            if (latest != null && latest.getRev() != rev) {
                RequestHandler.makeError(outputStream, "Rev log changed.");
                return;
            }
            branchLog.add(logMessage, added);
        } finally {
            branchLog.unlock();
        }

        Request.writeRequestHeader(outputStream, PUT_CHUNKS);
    }
}
