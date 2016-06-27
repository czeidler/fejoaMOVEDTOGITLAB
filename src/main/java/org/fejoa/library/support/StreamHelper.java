/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;

import java.io.*;


public class StreamHelper {
    static public int BUFFER_SIZE = 1024;

    static public void copyBytes(InputStream inputStream, OutputStream outputStream, int size) throws IOException {
        int bufferLength = BUFFER_SIZE;
        byte[] buf = new byte[bufferLength];
        int bytesRead = 0;
        while (bytesRead < size) {
            int requestedBunchSize = Math.min(size - bytesRead, bufferLength);
            int read = inputStream.read(buf, 0, requestedBunchSize);
            bytesRead += read;
            outputStream.write(buf, 0, read);
        }
    }

    static public void copy(InputStream inputStream, DataOutput outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) > 0)
            outputStream.write(buffer, 0, length);
    }

    static public void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) > 0)
            outputStream.write(buffer, 0, length);
    }

    static public void copy(InputStream inputStream, Writer writer) throws IOException {
        char[] buffer = new char[BUFFER_SIZE];
        int length;
        InputStreamReader reader = new InputStreamReader(inputStream);
        while ((length = reader.read(buffer)) > 0)
            writer.write(buffer, 0, length);
    }

    static public byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        copy(inputStream, outputStream);
        return outputStream.toByteArray();
    }
}
