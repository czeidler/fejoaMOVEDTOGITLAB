/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.util.MultiPartInputStreamParser;
import org.fejoa.library.support.StreamHelper;

import javax.servlet.ServletException;
import javax.servlet.http.Part;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class HTMLRequest implements IRemoteRequest {
    final private CookieStore cookieStore;
    private String url;
    private HttpPost httpPost;
    private boolean canceled = false;

    static final public String MESSAGE_KEY = "message";
    static final public String DATA_KEY = "data";
    static final public String DATA_FILE = "binary.data";

    public HTMLRequest(String url, CookieStore cookieStore) {
        this.url = url;
        this.cookieStore = cookieStore;
    }

    @Override
    public RemoteMessage send(RemoteMessage message) throws IOException {
        return getHTML(message);
    }

    @Override
    public void cancel() {
        canceled = true;
        HttpPost httpPostStrongRef = httpPost;
        if (httpPostStrongRef != null)
            httpPostStrongRef.abort();
    }

    private RemoteMessage getHTML(RemoteMessage data) throws IOException {
        System.out.println("SEND:     " + new String(data.message));
        HttpClient httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
        httpPost = new HttpPost(url);

        if (canceled)
            return null;

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addTextBody(MESSAGE_KEY, data.message);
        if (data.binaryData != null)
            builder.addBinaryBody(DATA_KEY, data.binaryData, ContentType.DEFAULT_BINARY, DATA_FILE);

        httpPost.setEntity(builder.build());
        try {
            HttpResponse response = httpClient.execute(httpPost);

            if (response.getStatusLine().getStatusCode() != 200)
                throw new IOException("Unexpected status code: " + response.getStatusLine().getStatusCode());


            InputStream inputStream = response.getEntity().getContent();
            String line = "";
            for (int character = inputStream.read(); character >= 0 && character != '\n';
                 character = inputStream.read()) {
                line += (char)(character);
            }
            line = line.replace("Content-Type: ", "");
            MultiPartInputStreamParser parser = new MultiPartInputStreamParser(inputStream,
                    line, null, null);
            try {
                Part messagePart = parser.getPart(MESSAGE_KEY);
                Part dataPart = parser.getPart(DATA_KEY);

                BufferedInputStream bufferedInputStream = new BufferedInputStream(messagePart.getInputStream());
                ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
                StreamHelper.copy(bufferedInputStream, receivedData);

                String message = receivedData.toString();
                System.out.println("RECEIVED: " + message);

                return new RemoteMessage(message, dataPart.getInputStream());
            } catch (ServletException e) {
                e.printStackTrace();
                throw new IOException("Unexpected server response.");
            }
        } finally {
            httpPost.releaseConnection();
            httpPost = null;
        }
    }

}
