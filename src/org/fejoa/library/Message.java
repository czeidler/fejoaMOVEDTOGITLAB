package org.fejoa.library;


import org.fejoa.library.crypto.*;
import org.fejoa.library.support.PositionInputStream;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;


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

class ChannelBranch {
    private String branchName;
    // used to encrypt/decrypt data in the channel
    private byte[] iv;
    private SecretKey symmetricKey;
    // used to verify that user is allowed to get access to the channel data
    private PrivateKey signatureKey;
    private PublicKey signatureKeyPublic;

    class ChannelBranchWriter implements IParcelEnvelopeWriter{
        @Override
        public byte[] pack(byte[] parcel) throws IOException {
            ByteArrayOutputStream pack = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(pack);

            stream.writeBytes(branchName + "\n");

            stream.writeInt(iv.length);
            stream.write(iv, 0, iv.length);

            byte[] raw = symmetricKey.getEncoded();
            stream.writeInt(raw.length);
            stream.write(raw, 0, raw.length);

            raw = CryptoHelper.convertToPEM(signatureKey).getBytes();
            stream.writeInt(raw.length);
            stream.write(raw, 0, raw.length);

            return pack.toByteArray();
        }
    }

    class ChannelBranchReader implements IParcelEnvelopeReader {
        @Override
        public byte[] unpack(byte[] parcel) throws IOException, CryptoException {
            ByteArrayInputStream positionInputStream = new ByteArrayInputStream(parcel);
            DataInputStream stream = new DataInputStream(positionInputStream);

            branchName = stream.readLine();

            int length = stream.readInt();
            iv = new byte[length];
            stream.readFully(iv, 0, iv.length);

            // sym key
            length = stream.readInt();
            byte[] raw = new byte[length];
            stream.readFully(raw, 0, raw.length);
            symmetricKey = CryptoHelper.symmetricKeyFromRaw(raw);

            // signature key
            length = stream.readInt();
            raw = new byte[length];
            stream.readFully(raw, 0, raw.length);
            signatureKey = CryptoHelper.privateKeyFromPem(new String(raw));

            return null;
        }
    }

    public ChannelBranch(UserIdentity identity, PublicKey signatureKey, byte[] pack)
            throws IOException, CryptoException {
        signatureKeyPublic = signatureKey;

        ChannelBranchReader channelBranchReader =  new ChannelBranchReader();
        SecureAsymEnvelopeReader secureEnvelopeReader = new SecureAsymEnvelopeReader(identity.getMyself(),
                channelBranchReader);
        SignatureEnvelopeReader signatureReader = new SignatureEnvelopeReader(identity.getContactFinder(),
                secureEnvelopeReader);

        signatureReader.unpack(pack);
    }

    public ChannelBranch() throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        byte hashResult[] = CryptoHelper.sha1Hash(crypto.generateInitializationVector(40));
        branchName = CryptoHelper.toHex(hashResult);

        iv = crypto.generateInitializationVector(CryptoSettings.SYMMETRIC_KEY_IV_SIZE);
        symmetricKey = crypto.generateSymmetricKey(CryptoSettings.SYMMETRIC_KEY_SIZE);

        KeyPair keyPair = crypto.generateKeyPair(CryptoSettings.ASYMMETRIC_KEY_SIZE);
        signatureKey = keyPair.getPrivate();
        signatureKeyPublic = keyPair.getPublic();
    }

    public byte[] pack(ContactPrivate sender, KeyId senderKey, Contact receiver, KeyId receiverKey)
            throws CryptoException, IOException {
        ChannelBranchWriter channelBranchWriter = new ChannelBranchWriter();
        SecureAsymEnvelopeWriter asymEnvelopeWriter = new SecureAsymEnvelopeWriter(receiver, receiverKey,
                channelBranchWriter);
        SignatureEnvelopeWriter signatureEnvelopeWriter = new SignatureEnvelopeWriter(sender, senderKey,
                asymEnvelopeWriter);

        return signatureEnvelopeWriter.pack(null);
    }
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

        stream.writeBytes(message.getMessageChannelInfo().getUid() + "\n");
        stream.writeBytes(message.getBody());

        return pack.toByteArray();
    }
}

class MessageEnvelopeReader implements IParcelEnvelopeReader {

    @Override
    public byte[] unpack(byte[] parcel) throws IOException {
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
        SecureAsymEnvelopeWriter secureAsymEnvelopeWriter = new SecureAsymEnvelopeWriter(receiver, receiverKey,
                messageEnvelopeWriter);
        SignatureEnvelopeWriter signatureEnvelopeWriter
                = new SignatureEnvelopeWriter(sender, senderKeyId, secureAsymEnvelopeWriter);
        return signatureEnvelopeWriter.pack(null);
    }
}