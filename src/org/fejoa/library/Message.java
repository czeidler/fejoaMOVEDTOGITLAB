package org.fejoa.library;


import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.PositionInputStream;

import java.io.*;


public class Message {
    private MessageChannelInfo messageChannelInfo;

    private String body;

    public MessageChannelInfo getMessageChannelInfo() {
        return messageChannelInfo;
    }

    public String getBody() {
        return body;
    }
}

class MessageEnvelopeWriter implements IParcelEnvelopeWriter{
    private Message message;

    public MessageEnvelopeWriter(Message message) {
        this.message = message;
    }

    @Override
    public byte[] pack() throws IOException {
        ByteArrayOutputStream pack = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(pack);

        stream.writeBytes(message.getMessageChannelInfo().getUid() + "\n");
        stream.writeBytes(message.getBody());

        return pack.toByteArray();
    }
}

class MessageEnvelopeReader implements IParcelEnvelopeReader {

    @Override
    public byte[] unpack(byte[] parcel) throws Exception {
        PositionInputStream positionInputStream = new PositionInputStream(new ByteArrayInputStream(parcel));
        DataInputStream stream = new DataInputStream(positionInputStream);

        String messageChannelInfoUid = stream.readLine();

        byte bodyData[] = new byte[parcel.length - (int)positionInputStream.getPosition()];
        stream.readFully(bodyData);
        String body = new String(bodyData);

        return null;
    }
}

class MessageEnveloper {

    private Message message;
    public byte[] envelope(ContactPrivate sender, KeyId senderKeyId, ContactPublic receiver, KeyId receiverKey)
            throws CryptoException, IOException {

        MessageEnvelopeWriter messageEnvelopeWriter = new MessageEnvelopeWriter(message);
        SecureEnvelopeWriter secureEnvelopeWriter = new SecureEnvelopeWriter(receiver, receiverKey,
                messageEnvelopeWriter);
        SignatureEnvelopeWriter signatureEnvelopeWriter
                = new SignatureEnvelopeWriter(sender, senderKeyId, secureEnvelopeWriter);
        return signatureEnvelopeWriter.pack();
    }
}