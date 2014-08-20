/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.support.ProtocolOutStream;
import rx.Observable;
import rx.Observer;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;


public interface IRemoteRequest {
    public String getUrl();
    public Observable<byte[]> send(byte data[]);
}

class RemoteRequestHelper {
    static public byte[] send(final IRemoteRequest remoteRequest, byte out[]) throws IOException {
        final byte[][] reply = new byte[1][1];
        final Throwable[] error = {null};
        Observable<byte[]> remote = remoteRequest.send(out);
        remote.subscribe(new Observer<byte[]>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable throwable) {
                error[0] = throwable;
            }

            @Override
            public void onNext(byte[] bytes) {
                reply[0] = bytes;
            }
        });

        if (error[0] != null)
            throw new IOException(error[0].getMessage());

        return reply[0];
    }

    static public byte[] send(final IRemoteRequest remoteRequest, ProtocolOutStream outStream)
            throws TransformerException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out);
        outStream.write(writer);

        return RemoteConnection.send(remoteRequest, out.toByteArray());
    }
}
