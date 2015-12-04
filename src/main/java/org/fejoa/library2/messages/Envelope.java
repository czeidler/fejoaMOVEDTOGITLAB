/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.messages;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library2.ContactPrivate;
import org.fejoa.library2.FejoaContext;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;


public class Envelope {
    static final public String PACK_TYPE_KEY = "type";
    static final public String CONTAINS_DATA_KEY = "data";

    static public byte[] unpack(byte[] pack, ContactPrivate contact, FejoaContext context) throws IOException,
            JSONException, CryptoException {
        int newLinePos = 0;
        for (; newLinePos < pack.length; newLinePos++) {
            if (pack[newLinePos] == '\n')
                break;
        }
        String header = new String(pack, 0, newLinePos);
        JSONObject object = new JSONObject(header);
        String packType = object.getString(PACK_TYPE_KEY);

        InputStream inputStream = new ByteArrayInputStream(pack);
        inputStream.skip(newLinePos + 1);
        byte[] data;
        switch (packType) {
            case SignatureEnvelope.SIGNATURE_TYPE:
                data = SignatureEnvelope.verify(object, inputStream, contact);
                break;
            case ZipEnvelope.ZIP_TYPE:
                data = ZipEnvelope.unzip(object, inputStream);
                break;
            case PublicCryptoEnvelope.CRYPTO_TYPE:
                data = PublicCryptoEnvelope.decrypt(object, inputStream, contact, context);
                break;
            default:
                throw new IOException("Unknown pack type: " + packType);
        }
        if (object.has(CONTAINS_DATA_KEY) && object.getInt(CONTAINS_DATA_KEY) > 0)
            return data;
        return unpack(data, contact, context);
    }
}
