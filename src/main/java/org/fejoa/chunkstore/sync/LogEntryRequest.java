/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.ChunkStoreBranchLog;
import org.fejoa.library.remote.IRemotePipe;
import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.fejoa.chunkstore.sync.Request.GET_REMOTE_TIP;

public class LogEntryRequest {
    static public ChunkStoreBranchLog.Entry getRemoteTip(IRemotePipe remotePipe, String branch) throws IOException {
        DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
        Request.writeRequestHeader(outputStream, GET_REMOTE_TIP);
        StreamHelper.writeString(outputStream, branch);

        DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
        Request.receiveHeader(inputStream, GET_REMOTE_TIP);

        String header = StreamHelper.readString(inputStream);
        return ChunkStoreBranchLog.Entry.fromHeader(header);
    }
}
