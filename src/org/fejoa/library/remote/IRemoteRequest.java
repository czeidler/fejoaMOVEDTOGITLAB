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
    static public Observable<byte[]> send(final IRemoteRequest remoteRequest, ProtocolOutStream outStream)
            throws TransformerException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out);
        outStream.write(writer);

        return remoteRequest.send(out.toByteArray());
    }

    static public byte[] sendSync(final IRemoteRequest remoteRequest, byte[] bytes) {
        final byte[][] result = {null};
        remoteRequest.send(bytes).subscribe(new Observer<byte[]>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(byte[] args) {
                result[0] = args;
            }
        });

        return result[0];
    }
}
