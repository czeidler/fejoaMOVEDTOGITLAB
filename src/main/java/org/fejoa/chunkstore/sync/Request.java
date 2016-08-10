/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class Request {
    static final public int ERROR = -1;
    static final public int PULL_REQUEST_VERSION = 1;
    static final public int GET_REMOTE_TIP = 1;
    static final public int GET_CHUNKS = 2;
    static final public int PUT_CHUNKS = 3;
    static final public int HAS_CHUNKS = 4;

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
        if (response <= ERROR)
            throw new IOException("ERROR: " + StreamHelper.readString(inputStream));
        if (response != request)
            throw new IOException("GET_REMOTE_TIP response expected but got: " + response);
    }
}
