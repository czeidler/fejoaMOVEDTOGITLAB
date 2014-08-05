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
    private String body;

    public String getBody() {
        return body;
    }

    public void load(SecureStorageDir dir, UserIdentity userIdentity) throws IOException, CryptoException {
        byte[] pack = dir.readBytes("m");
        unpack(userIdentity, pack);
    }

    public void write(SecureStorageDir dir, ContactPrivate sender, KeyId senderKeyId, ContactPublic receiver,
                      KeyId receiverKey) throws IOException, CryptoException {
        byte[] pack = pack(sender, senderKeyId, receiver, receiverKey);

        dir.writeBytes("m", pack);
    }

    private byte[] unpack(UserIdentity identity, byte[] pack) throws IOException, CryptoException {
        MessageEnvelopeReader messageEnvelopeReader =  new MessageEnvelopeReader();
        SecureAsymEnvelopeReader secureEnvelopeReader = new SecureAsymEnvelopeReader(identity.getMyself(),
                messageEnvelopeReader);
        SignatureEnvelopeReader signatureReader = new SignatureEnvelopeReader(identity.getContactFinder(),
                secureEnvelopeReader);

        return signatureReader.unpack(pack);
    }

    private byte[] pack(ContactPrivate sender, KeyId senderKeyId, ContactPublic receiver, KeyId receiverKey)
            throws CryptoException, IOException {

        MessageEnvelopeWriter messageEnvelopeWriter = new MessageEnvelopeWriter(this);
        SecureAsymEnvelopeWriter secureAsymEnvelopeWriter = new SecureAsymEnvelopeWriter(receiver, receiverKey,
                messageEnvelopeWriter);
        SignatureEnvelopeWriter signatureEnvelopeWriter
                = new SignatureEnvelopeWriter(sender, senderKeyId, secureAsymEnvelopeWriter);
        return signatureEnvelopeWriter.pack(null);
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

