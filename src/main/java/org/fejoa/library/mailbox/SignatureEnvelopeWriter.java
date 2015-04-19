/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class SignatureEnvelopeWriter implements IParcelEnvelopeWriter {
    String uid;
    final ContactPrivate sender;
    final KeyId signatureKey;
    byte signature[];
    IParcelEnvelopeWriter childWriter;
    final CryptoSettings signatureSettings;

    public SignatureEnvelopeWriter(ContactPrivate sender, KeyId signatureKey, CryptoSettings signatureSettings,
                                   IParcelEnvelopeWriter childWriter) {
        this.signatureSettings = signatureSettings;
        this.sender = sender;
        this.signatureKey = signatureKey;
    }

    /**
     * The uid is calculated in pack. The uid is the sha hash of the packed data. Note, that this might be different
     * to the sha hash of the data argument in pack since the written data can come from the childWriter.
     *
     * @return the uid (pack needed to be called)
     */
    public String getUid() {
        return uid;
    }

    @Override
    public byte[] pack(byte[] parcel) throws IOException, CryptoException {
        /*
         Structure:
         -- data length int
         -- data
         -- sender uid \n
         -- signature key id \n
         - signature length int
         - signature
         */
        uid = CryptoHelper.toHex(CryptoHelper.sha1Hash(parcel));

        ByteArrayOutputStream packageData = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(packageData);

        // data
        stream.writeInt(parcel.length);
        stream.write(parcel, 0, parcel.length);

        // sender and key id
        stream.writeBytes(sender.getUid() + "\n");
        stream.writeBytes(signatureSettings.signatureAlgorithm + "\n");
        stream.writeBytes(signatureKey.getKeyId() + "\n");

        // signature
        String signatureHash = CryptoHelper.toHex(CryptoHelper.sha256Hash(packageData.toByteArray()));
        signature = sender.sign(signatureKey, signatureHash.getBytes(), signatureSettings);
        stream.writeInt(signature.length);
        stream.write(signature, 0, signature.length);

        byte[] pack = packageData.toByteArray();
        if (childWriter != null)
            return childWriter.pack(pack);
        return pack;
    }
}
