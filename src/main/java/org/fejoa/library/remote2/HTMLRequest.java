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
import java.net.MalformedURLException;
import java.net.URL;


public class HTMLRequest implements IRemoteRequest {
    final private String url;
    private HttpURLConnection connection;
    private InputStream inputStream;

    static final public String MESSAGE_KEY = "header";
    static final public String DATA_KEY = "data";
    static final public String DATA_FILE = "binary.data";

    public HTMLRequest(String url) {
        this.url = url;
    }

    @Override
    public RemoteJob.Result send(RemoteJob job) throws IOException {
        System.out.println("SEND:     " + new String(job.getHeader()));

        OutputStream outputStream = null;
        try {
            URL server = new URL(url);

            final String boundary = "===" + System.currentTimeMillis() + "===";
            final String LINE_FEED = "\r\n";

            connection = (HttpURLConnection)server.openConnection();
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Accept-Charset", "utf-8");

            connection.connect();
            outputStream = connection.getOutputStream();

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);
            // header
            writer.append("--" + boundary).append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"" + HTMLRequest.MESSAGE_KEY + "\"").append(LINE_FEED);
            writer.append(LINE_FEED);
            writer.append(job.getHeader()).append(LINE_FEED);

            // body
            if (job.hasData()) {
                writer.append("--" + boundary).append(LINE_FEED);
                writer.append("Content-Type: \"application/octet-stream\"").append(LINE_FEED);
                writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
                writer.append("Content-Disposition: form-data; name=\"" + HTMLRequest.DATA_KEY
                        + "\"; filename=\"" + HTMLRequest.DATA_FILE + "\"").append(LINE_FEED);
                writer.append(LINE_FEED);
                writer.flush();
                job.writeData(outputStream);
                writer.append(LINE_FEED);
            }

            // finish
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
            InputStream responseStream = null;
            try {
                Part messagePart = parser.getPart(HTMLRequest.MESSAGE_KEY);
                Part dataPart = parser.getPart(HTMLRequest.DATA_KEY);

                BufferedInputStream bufferedInputStream = new BufferedInputStream(messagePart.getInputStream());
                ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
                StreamHelper.copy(bufferedInputStream, receivedData);

                String message = receivedData.toString();
                System.out.println("RECEIVED: " + message);

                responseStream = (dataPart == null) ? null : dataPart.getInputStream();
                return job.handleResponse(message, responseStream);
            } catch (ServletException e) {
                e.printStackTrace();
                throw new IOException("Unexpected server response.");
            } finally {
                if (responseStream != null)
                    responseStream.close();
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        } finally {
            //close the connection, set all objects to null
            if (outputStream != null)
                outputStream.close();
            if (inputStream != null)
                inputStream.close();
            if (connection != null)
                connection.disconnect();
        }
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
