package org.fejoa.library.mailbox;

import org.fejoa.library.Contact;
import org.fejoa.library.IContactFinder;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SignatureEnvelopeReader implements IParcelEnvelopeReader {
    private String uid = "";
    private IContactFinder contactFinder;
    private IParcelEnvelopeReader childReader;

    public SignatureEnvelopeReader(IContactFinder contactFinder,
                                   IParcelEnvelopeReader childReader) {
        this.contactFinder = contactFinder;
        this.childReader = childReader;
    }

    public String getUid() {
        return uid;
    }

    @Override
    public byte[] unpack(byte[] parcel) throws IOException, CryptoException {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(parcel));

        // data
        int length = stream.readInt();
        byte[] data = new byte[length];
        stream.readFully(data, 0, data.length);

        uid = CryptoHelper.toHex(CryptoHelper.sha1Hash(data));

        MessageDigest hashForSignature = null;
        try {
            hashForSignature = CryptoHelper.sha256Hash();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new IOException("no sha1 available");
        }
        hashForSignature.update(ByteBuffer.allocate(4).putInt(length).array());
        hashForSignature.update(data);

        // verify signature
        String senderUid = stream.readLine();
        KeyId signatureKey = new KeyId(stream.readLine());

        hashForSignature.update((senderUid + "\n").getBytes());
        hashForSignature.update((signatureKey.getKeyId() + "\n").getBytes());
        String signatureHash = CryptoHelper.toHex(hashForSignature.digest());

        length = stream.readInt();
        byte[] signature = new byte[length];
        stream.readFully(signature, 0, signature.length);

        // validate data
        Contact sender = contactFinder.find(senderUid);
        if (sender == null)
            throw new IOException("contact not found");
        if (!sender.verify(signatureKey, signatureHash.getBytes(), signature))
            throw new IOException("can't be verified");

        if (childReader != null)
            return childReader.unpack(data);
        return data;
    }
}
