/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.messages;


import org.apache.commons.io.output.ByteArrayOutputStream;
import org.fejoa.library.support.StreamHelper;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.zip.*;


public class ZipEnvelope {
    static final public String ZIP_TYPE = "zip";
    static final private String ZIP_FORMAT_KEY = "format";
    static final private String ZIP_FORMAT = "gzip";

    static public InputStream zip(InputStream data, boolean isRawData) throws JSONException, IOException {
        JSONObject object = new JSONObject();
        object.put(Envelope.PACK_TYPE_KEY, ZIP_TYPE);
        if (isRawData)
            object.put(Envelope.CONTAINS_DATA_KEY, 1);
        object.put(ZIP_FORMAT_KEY, ZIP_FORMAT);
        String header = object.toString() + "\n";

        return new SequenceInputStream(new ByteArrayInputStream(header.getBytes()), new DeflaterInputStream(data));
    }

    static public InputStream unzipStream(JSONObject header, InputStream inputStream) throws IOException, JSONException {
        // verify
        if (!header.getString(ZIP_FORMAT_KEY).equals(ZIP_FORMAT))
            throw new IOException("Unsupported zip format: " + header.getString(ZIP_FORMAT_KEY));

        return new InflaterInputStream(inputStream);
    }

    static public byte[] zip(byte[] data, boolean isRawData) throws JSONException, IOException {
        JSONObject object = new JSONObject();
        object.put(Envelope.PACK_TYPE_KEY, ZIP_TYPE);
        if (isRawData)
            object.put(Envelope.CONTAINS_DATA_KEY, 1);
        object.put(ZIP_FORMAT_KEY, ZIP_FORMAT);
        String header = object.toString() + "\n";

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        outStream.write(header.getBytes());
        DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(outStream);
        deflaterOutputStream.write(data);
        deflaterOutputStream.finish();
        outStream.close();
        return outStream.toByteArray();
    }

    static public byte[] unzip(JSONObject header, InputStream inputStream) throws IOException, JSONException {
        // verify
        if (!header.getString(ZIP_FORMAT_KEY).equals(ZIP_FORMAT))
            throw new IOException("Unsupported zip format: " + header.getString(ZIP_FORMAT_KEY));

        InflaterInputStream inflaterInputStream = new InflaterInputStream(inputStream);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        StreamHelper.copy(inflaterInputStream, outStream);
        return outStream.toByteArray();
    }
}
