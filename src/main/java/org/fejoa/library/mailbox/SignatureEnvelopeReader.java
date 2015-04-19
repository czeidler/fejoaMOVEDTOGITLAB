/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.Contact;
import org.fejoa.library.IContactFinder;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class SignatureEnvelopeReader implements IParcelEnvelopeReader {
    String uid = "";
    final IContactFinder contactFinder;
    final CryptoSettings signatureSettings;
    final IParcelEnvelopeReader childReader;

    public SignatureEnvelopeReader(IContactFinder contactFinder, CryptoSettings signatureSettings,
                                   IParcelEnvelopeReader childReader) {
        this.contactFinder = contactFinder;
        this.signatureSettings = signatureSettings;
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
        signatureSettings.signatureAlgorithm = stream.readLine();
        KeyId signatureKey = new KeyId(stream.readLine());

        hashForSignature.update((senderUid + "\n").getBytes());
        hashForSignature.update((signatureSettings.signatureAlgorithm + "\n").getBytes());
        hashForSignature.update((signatureKey.getKeyId() + "\n").getBytes());
        String signatureHash = CryptoHelper.toHex(hashForSignature.digest());

        length = stream.readInt();
        byte[] signature = new byte[length];
        stream.readFully(signature, 0, signature.length);

        // validate data
        Contact sender = contactFinder.find(senderUid);
        if (sender == null)
            throw new IOException("contact not found");
        if (!sender.verify(signatureKey, signatureHash.getBytes(), signature, signatureSettings))
            throw new IOException("can't be verified");

        if (childReader != null)
            return childReader.unpack(data);
        return data;
    }
}
