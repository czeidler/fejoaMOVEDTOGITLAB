/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.support.StreamHelper;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class HTMLRequest implements IRemoteRequest {
    private String url;

    public HTMLRequest(String url) {
        this.url = url;
    }

    @Override
    public Observable<byte[]> send(final byte data[]) {
        return Observable.create(new Func1<Observer<byte[]>, Subscription>() {
            @Override
            public Subscription call(final Observer<byte[]> receiver) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        byte receivedData[] = new byte[0];
                        try {
                            receivedData = getHTML(url, data);
                        } catch (IOException e) {
                            receiver.onError(e);
                        }

                        receiver.onNext(receivedData);
                        receiver.onCompleted();
                    }
                }).start();

                return Subscriptions.empty();
            }
        });
    }

    private byte[] getHTML(String urlToRead, byte data[]) throws IOException {
        // creates a unique boundary based on time stamp
        final String boundary = "===" + System.currentTimeMillis() + "===";
        final String LINE_FEED = "\r\n";
        final URL url = new URL(urlToRead);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        BufferedInputStream bufferedInputStream = null;
        ByteArrayOutputStream receivedData = null;
        try {
            connection.setUseCaches(false);
            connection.setDoOutput(true); // indicates POST method
            connection.setDoInput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Accept-Charset", "utf-8");

            OutputStream outputStream = connection.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);

            // header
            writer.append("--" + boundary).append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"transfer_data\"").append(LINE_FEED);
            writer.append(LINE_FEED);
            writer.append("transfer_data.txt").append(LINE_FEED);

            // body
            writer.append("--" + boundary).append(LINE_FEED);
            writer.append("Content-Type: \"text/plain\"").append(LINE_FEED);
            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"transfer_data\"; filename=\"transfer_data.txt\"")
                    .append(LINE_FEED);
            writer.append(LINE_FEED);
            writer.flush();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            StreamHelper.copy(inputStream, outputStream);
            writer.append(LINE_FEED);

            // finish
            writer.append("--" + boundary + "--").append(LINE_FEED);
            writer.append(LINE_FEED).flush();
            writer.close();

            // receive
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Bad server response: " + status);
            }

            receivedData = new ByteArrayOutputStream();
            bufferedInputStream = new BufferedInputStream(connection.getInputStream());
            StreamHelper.copy(bufferedInputStream, receivedData);
        } finally {
            if (bufferedInputStream != null)
                bufferedInputStream.close();
            connection.disconnect();
        }
        return receivedData.toByteArray();
    }
}
