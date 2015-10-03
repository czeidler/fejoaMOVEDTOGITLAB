/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.*;
import org.fejoa.library.crypto.*;
import org.fejoa.library.support.PositionInputStream;

import java.io.*;


public class Message {
    private String uid;
    private String body;

    public String getUid() {
        return uid;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    public void load(ParcelCrypto parcelCrypto, UserIdentity identity, byte[] pack) throws IOException,
            CryptoException {
        MessageEnvelopeReader messageEnvelopeReader =  new MessageEnvelopeReader();
        SecureSymEnvelopeReader secureEnvelopeReader = new SecureSymEnvelopeReader(parcelCrypto,
                messageEnvelopeReader);
        CryptoSettings.SignatureSettings signatureSettings = CryptoSettings.empty().signature;
        SignatureEnvelopeReader signatureReader = new SignatureEnvelopeReader(identity.getContactFinder(),
                signatureSettings, secureEnvelopeReader);

        byte[] result = signatureReader.unpack(pack);
        uid = signatureReader.getUid();
    }

    public byte[] write(ParcelCrypto parcelCrypto, ContactPrivate sender, KeyId senderKeyId,
                        CryptoSettings.SignatureSettings signatureSettings) throws IOException,
            CryptoException {
        SignatureEnvelopeWriter signatureEnvelopeWriter = new SignatureEnvelopeWriter(sender, senderKeyId,
                signatureSettings, null);
        SecureSymEnvelopeWriter secureSymEnvelopeWriter = new SecureSymEnvelopeWriter(parcelCrypto,
                signatureEnvelopeWriter);
        MessageEnvelopeWriter messageEnvelopeWriter = new MessageEnvelopeWriter(this, secureSymEnvelopeWriter);


        byte[] result = messageEnvelopeWriter.pack(null);
        uid = signatureEnvelopeWriter.getUid();
        return result;
    }

    class MessageEnvelopeWriter implements IParcelEnvelopeWriter{
        final private Message message;
        final private IParcelEnvelopeWriter childWriter;

        public MessageEnvelopeWriter(Message message, IParcelEnvelopeWriter childWriter) {
            this.message = message;
            this.childWriter = childWriter;
        }

        @Override
        public byte[] pack(byte[] parcel) throws IOException, CryptoException {
            ByteArrayOutputStream pack = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(pack);

            stream.writeBytes(message.getBody());

            byte[] out = pack.toByteArray();
            if (childWriter != null)
                return childWriter.pack(out);
            return out;
        }
    }

    class MessageEnvelopeReader implements IParcelEnvelopeReader {

        @Override
        public byte[] unpack(byte[] parcel) throws IOException {
            PositionInputStream positionInputStream = new PositionInputStream(new ByteArrayInputStream(parcel));
            DataInputStream stream = new DataInputStream(positionInputStream);

            byte bodyData[] = new byte[parcel.length - (int)positionInputStream.getPosition()];
            stream.readFully(bodyData);
            body = new String(bodyData);

            return null;
        }
    }
}

