/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.messages;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;


public class PlainEnvelope {
    static final public String PLAIN_TYPE = "plain";

    static public InputStream pack(InputStream data, boolean isRawData) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(Envelope.PACK_TYPE_KEY, PLAIN_TYPE);
        if (isRawData)
            object.put(Envelope.CONTAINS_DATA_KEY, 1);
        String header = object.toString() + "\n";

        return new SequenceInputStream(new ByteArrayInputStream(header.getBytes()), data);
    }

    static public InputStream unpack(InputStream inputStream) {
        return inputStream;
    }
}
