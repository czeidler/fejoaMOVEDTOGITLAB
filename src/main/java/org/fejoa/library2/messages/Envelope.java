/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.messages;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library2.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;


public class Envelope {
    static final public String PACK_TYPE_KEY = "type";
    static final public String CONTAINS_DATA_KEY = "data";

    private String senderId;

    public InputStream unpack(InputStream pack, ContactPrivate contact,
                                     IContactFinder<IContactPublic> contactFinder, FejoaContext context)
            throws IOException, JSONException, CryptoException {
        int newLinePos = 0;
        final int BUFFER_SIZE = 1024;
        byte[] headerBuffer = new byte[BUFFER_SIZE];
        for (int ch = pack.read(); ch >= 0; ch = pack.read()) {
            if (ch == '\n')
                break;
            headerBuffer[newLinePos] = (byte)ch;
            newLinePos++;
            if (newLinePos >= BUFFER_SIZE)
                throw new IOException("header is too long");
        }
        String header = new String(headerBuffer, 0, newLinePos);
        JSONObject object = new JSONObject(header);
        String packType = object.getString(PACK_TYPE_KEY);

        InputStream result;
        switch (packType) {
            case PlainEnvelope.PLAIN_TYPE:
                result = PlainEnvelope.unpack(pack);
                break;
            case SignatureEnvelope.SIGNATURE_TYPE:
                SignatureEnvelope.ReturnValue sigReturn = SignatureEnvelope.verifyStream(object, pack, contactFinder);
                result = sigReturn.inputStream;
                senderId = sigReturn.senderId;
                break;
            case ZipEnvelope.ZIP_TYPE:
                result = ZipEnvelope.unzipStream(object, pack);
                break;
            case PublicCryptoEnvelope.CRYPTO_TYPE:
                result = PublicCryptoEnvelope.decryptStream(object, pack, contact, context);
                break;
            default:
                throw new IOException("Unknown pack type: " + packType);
        }
        if (object.has(CONTAINS_DATA_KEY) && object.getInt(CONTAINS_DATA_KEY) > 0)
            return result;
        return unpack(result, contact, contactFinder, context);
    }

    public String getSenderId() {
        return senderId;
    }

    static public byte[] unpack(byte[] pack, ContactPrivate contact, IContactFinder<IContactPublic> contactFinder,
                                FejoaContext context) throws IOException, JSONException, CryptoException {
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
                data = SignatureEnvelope.verify(object, inputStream, contactFinder);
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
        return unpack(data, contact, contactFinder, context);
    }
}
