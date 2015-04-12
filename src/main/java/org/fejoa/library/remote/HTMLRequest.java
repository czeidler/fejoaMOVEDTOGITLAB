/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.*;
import org.fejoa.library.support.StreamHelper;

import java.io.*;

/*
public class HTMLRequest implements IRemoteRequest {
    //static private HttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    private String url;
    private HttpURLConnection httpPost;

    public HTMLRequest(String url) {
        this.url = url;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public byte[] send(byte[] data) throws IOException {
        return getHTML(data);
    }

    @Override
    public void cancel() {
        if (httpPost != null)
            httpPost.disconnect();
        httpPost = null;
    }

    private byte[] getHTML(byte data[]) throws IOException {
        httpPost = (HttpURLConnection)(new URL(url)).openConnection();

        // creates a unique boundary based on time stamp
        final String boundary = "===" + System.currentTimeMillis() + "===";
        final String LINE_FEED = "\r\n";
        ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
        BufferedInputStream bufferedInputStream = null;

        try {
            httpPost.setUseCaches(false);
            httpPost.setDoOutput(true); // indicates POST method
            httpPost.setDoInput(true);
            httpPost.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            httpPost.setRequestProperty("Accept-Charset", "utf-8");

            OutputStream outputStream = httpPost.getOutputStream();
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
            int status = httpPost.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Bad server response: " + status);
            }

            bufferedInputStream = new BufferedInputStream(httpPost.getInputStream());
            StreamHelper.copy(bufferedInputStream, receivedData);
        } finally {
            if (bufferedInputStream != null)
                bufferedInputStream.close();
            httpPost = null;
        }
        return receivedData.toByteArray();
    }
}*/

public class HTMLRequest implements IRemoteRequest {
    final private CookieStore cookieStore;
    private String url;
    private HttpPost httpPost;
    private boolean canceled = false;

    public HTMLRequest(String url, CookieStore cookieStore) {
        this.url = url;
        this.cookieStore = cookieStore;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public byte[] send(byte[] data) throws IOException {
        return getHTML(data);
    }

    @Override
    public void cancel() {
        canceled = true;
        HttpPost httpPostStrongRef = httpPost;
        if (httpPostStrongRef != null)
            httpPostStrongRef.abort();
    }

    private byte[] getHTML(byte data[]) throws IOException {
        System.out.println("SEND:     " + new String(data));
        HttpClient httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
        httpPost = new HttpPost(url);

        if (canceled)
            return new byte[0];

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addBinaryBody("transfer_data", data, ContentType.DEFAULT_BINARY, "transfer_data.txt");

        httpPost.setEntity(builder.build());
        try {
            HttpResponse response = httpClient.execute(httpPost);

            if (response.getStatusLine().getStatusCode() != 200)
                throw new IOException("Unexpected status code: " + response.getStatusLine().getStatusCode());

            ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
            BufferedInputStream bufferedInputStream = null;

            try {
                bufferedInputStream = new BufferedInputStream(response.getEntity().getContent());
                StreamHelper.copy(bufferedInputStream, receivedData);
            } finally {
                if (bufferedInputStream != null)
                    bufferedInputStream.close();
            }

            byte[] reply = receivedData.toByteArray();
            System.out.println("RECEIVED: " + new String(reply));

            return reply;
        } finally {
            httpPost.releaseConnection();
            httpPost = null;
        }
    }

}
