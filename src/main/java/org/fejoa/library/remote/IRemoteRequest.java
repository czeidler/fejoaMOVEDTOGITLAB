/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public interface IRemoteRequest {
    OutputStream open(String header, boolean outgoingData) throws IOException;

    String receiveHeader() throws IOException;
    InputStream receiveData() throws IOException;

    void close();
    void cancel();
}
