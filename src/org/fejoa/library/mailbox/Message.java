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

    public String getBody() {
        return body;
    }

    public void load(ParcelCrypto parcelCrypto, UserIdentity identity, byte[] pack) throws IOException,
            CryptoException {
        MessageEnvelopeReader messageEnvelopeReader =  new MessageEnvelopeReader();

        SecureSymEnvelopeReader secureEnvelopeReader = new SecureSymEnvelopeReader(parcelCrypto,
                messageEnvelopeReader);
        SignatureEnvelopeReader signatureReader = new SignatureEnvelopeReader(identity.getContactFinder(),
                secureEnvelopeReader);

        byte[] result = signatureReader.unpack(pack);
        uid = signatureReader.getUid();
    }

    public byte[] write(ParcelCrypto parcelCrypto, ContactPrivate sender, KeyId senderKeyId) throws IOException,
            CryptoException {
        MessageEnvelopeWriter messageEnvelopeWriter = new MessageEnvelopeWriter(this);

        SecureSymEnvelopeWriter secureSymEnvelopeWriter = new SecureSymEnvelopeWriter(parcelCrypto,
                messageEnvelopeWriter);
        SignatureEnvelopeWriter signatureEnvelopeWriter
                = new SignatureEnvelopeWriter(sender, senderKeyId, secureSymEnvelopeWriter);
        byte[] result = signatureEnvelopeWriter.pack(null);
        uid = signatureEnvelopeWriter.getUid();
        return result;
    }

    class MessageEnvelopeWriter implements IParcelEnvelopeWriter{
        private Message message;

        public MessageEnvelopeWriter(Message message) {
            this.message = message;
        }

        @Override
        public byte[] pack(byte[] parcel) throws IOException {
            ByteArrayOutputStream pack = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(pack);

            stream.writeBytes(message.getBody());

            return pack.toByteArray();
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

