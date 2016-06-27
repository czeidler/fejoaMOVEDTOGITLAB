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


/**
 * Provides an OutputStream to send data and an InputStream to receive the reply.
 *
 * Once starting reading from the InputStream no data can be send anymore.
 */
public class RemotePipe {
    private class RemoteInputStream extends InputStream {
        private InputStream rawInputStream;

        @Override
        public int read() throws IOException {
            if (outputStream.rawOutputStream != null)
                outputStream.rawOutputStream = null;

            if (rawInputStream == null) {
                if (onDataSentCallback != null)
                    onDataSentCallback.run();
                rawInputStream = remoteRequest.receiveData();
            }
            return rawInputStream.read();
        }
    }

    private class RemoteOutputStream extends OutputStream {
        private OutputStream rawOutputStream;

        @Override
        public void write(int i) throws IOException {
            if (inputStream.rawInputStream != null) {
                inputStream.rawInputStream = null;
                remoteRequest.close();
            }

            if (rawOutputStream == null)
                rawOutputStream = remoteRequest.open(header, true);

            rawOutputStream.write(i);
        }
    }

    final private String header;
    final private IRemoteRequest remoteRequest;
    final private RemoteInputStream inputStream = new RemoteInputStream();
    final private RemoteOutputStream outputStream = new RemoteOutputStream();
    final private Runnable onDataSentCallback;

    public RemotePipe(String header, IRemoteRequest remoteRequest, Runnable onDataSentCallback) {
        this.header = header;
        this.remoteRequest = remoteRequest;
        this.onDataSentCallback = onDataSentCallback;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

}
