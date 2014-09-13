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
import rx.Subscription;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;


public interface IRemoteRequest {
    public String getUrl();
    public byte[] send(byte data[]) throws IOException;
    public void cancel();
}

class RemoteRequestHelper {
    static public Observable<byte[]> send(final IRemoteRequest remoteRequest, ProtocolOutStream outStream)
            throws TransformerException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out);
        outStream.write(writer);

        return send(remoteRequest, out.toByteArray());
    }

    static public Observable<byte[]> send(final IRemoteRequest remoteRequest, final byte[] bytes) {
        return Observable.create(new Observable.OnSubscribeFunc<byte[]>() {
            @Override
            public Subscription onSubscribe(final Observer<? super byte[]> receiver) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        byte receivedData[];
                        try {
                            String out = new String(bytes);
                            System.out.print(out);
                            receivedData = remoteRequest.send(bytes);

                        } catch (IOException e) {
                            receiver.onError(e);
                            return;
                        }

                        String test = new String(receivedData);
                        System.out.print(test);

                        receiver.onNext(receivedData);
                        receiver.onCompleted();
                    }
                }).start();

                return new Subscription() {
                    @Override
                    public void unsubscribe() {
                        remoteRequest.cancel();
                    }
                };
            }
        });
    }
}
