/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.eclipse.jetty.util.MultiPartInputStreamParser;
import org.fejoa.library.support.StreamHelper;

import javax.servlet.ServletException;
import javax.servlet.http.Part;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;


public class HTMLRequest implements IRemoteRequest {
    final private String url;
    private HttpURLConnection connection;
    private PrintWriter writer;
    private String receivedHeader;
    private InputStream inputStream;
    private InputStream dataInputStream;

    static final public String MESSAGE_KEY = "header";
    static final public String DATA_KEY = "data";
    static final public String DATA_FILE = "binary.data";

    private String boundary = "===" + System.currentTimeMillis() + "===";
    static final private String LINE_FEED = "\r\n";

    public HTMLRequest(String url) {
        this.url = url;
    }

    @Override
    public OutputStream open(String header, boolean outgoingData) throws IOException {
        if (connection != null)
            throw new IOException("HTMLRequest already open!");

        System.out.println("SEND:     " + new String(header));

        URL server = new URL(url);

        connection = (HttpURLConnection)server.openConnection();
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Accept-Charset", "utf-8");

        connection.connect();
        OutputStream outputStream = connection.getOutputStream();

        writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);
        // header
        writer.append("--" + boundary).append(LINE_FEED);
        writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"" + HTMLRequest.MESSAGE_KEY + "\"").append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.append(header);

        // body
        if (outgoingData) {
            writer.append(LINE_FEED);
            writer.append("--" + boundary).append(LINE_FEED);
            writer.append("Content-Type: \"application/octet-stream\"").append(LINE_FEED);
            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"" + HTMLRequest.DATA_KEY
                    + "\"; filename=\"" + HTMLRequest.DATA_FILE + "\"").append(LINE_FEED);
            writer.append(LINE_FEED);
            writer.flush();
            return outputStream;
        }
        return null;
    }

    private void receive() throws IOException {
        if (receivedHeader != null)
            return;
        if (connection == null)
            throw new IOException("HTMLRequest not open!");

        // finish
        writer.append(LINE_FEED);
        writer.append("--" + boundary + "--").append(LINE_FEED);
        writer.append(LINE_FEED).flush();
        writer.close();

        inputStream = connection.getInputStream();

        String line = "";
        for (int character = inputStream.read(); character >= 0 && character != '\n';
             character = inputStream.read()) {
            line += (char)(character);
        }
        line = line.replace("Content-Type: ", "");
        MultiPartInputStreamParser parser = new MultiPartInputStreamParser(inputStream,
                line, null, null);
        try {
            Part messagePart = parser.getPart(HTMLRequest.MESSAGE_KEY);
            Part dataPart = parser.getPart(HTMLRequest.DATA_KEY);

            BufferedInputStream bufferedInputStream = new BufferedInputStream(messagePart.getInputStream());
            ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
            StreamHelper.copy(bufferedInputStream, receivedData);

            receivedHeader = receivedData.toString();
            System.out.println("RECEIVED: " + receivedHeader);

            dataInputStream = (dataPart == null) ? null : dataPart.getInputStream();
        } catch (ServletException e) {
            e.printStackTrace();
            throw new IOException("Unexpected server response.");
        }
    }

    @Override
    public String receiveHeader() throws IOException {
        receive();
        return receivedHeader;
    }

    @Override
    public InputStream receiveData() throws IOException {
        receive();
        return dataInputStream;
    }

    @Override
    public void close() {
        try {
            if (writer != null)
                writer.close();
            if (dataInputStream != null)
                dataInputStream.close();
            if (inputStream != null)
                inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (connection != null)
            connection.disconnect();

        writer = null;
        dataInputStream = null;
        inputStream = null;
        connection = null;
        receivedHeader = null;
    }

    @Override
    public void cancel() {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (connection != null)
            connection.disconnect();
    }
}
