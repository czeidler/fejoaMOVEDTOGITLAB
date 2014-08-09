/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.Contact;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class SecureAsymEnvelopeWriter implements IParcelEnvelopeWriter{
    private Contact receiver;
    private KeyId asymmetricKeyId;
    private ParcelCrypto parcelCrypto;
    private IParcelEnvelopeWriter childWriter;

    public SecureAsymEnvelopeWriter(Contact receiver, KeyId asymmetricKeyId, ParcelCrypto parcelCrypto,
                                    IParcelEnvelopeWriter childWriter) {
        this.receiver = receiver;
        this.asymmetricKeyId = asymmetricKeyId;
        this.parcelCrypto = parcelCrypto;
        this.childWriter = childWriter;
    }

    public ParcelCrypto getParcelCrypto() {
        return parcelCrypto;
    }

    @Override
    public byte[] pack(byte[] parcel) throws CryptoException, IOException {
        byte encryptedSymmetricKey[] = parcelCrypto.getEncryptedSymmetricKey(receiver, asymmetricKeyId);

        ByteArrayOutputStream packageData = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(packageData);

        // asym key id
        stream.writeBytes(asymmetricKeyId.getKeyId() + "\n");

        // encrypted sym key
        stream.writeInt(encryptedSymmetricKey.length);
        stream.write(encryptedSymmetricKey, 0, encryptedSymmetricKey.length);

        // iv
        byte iv[] = parcelCrypto.getIV();
        stream.writeInt(iv.length);
        stream.write(iv, 0, iv.length);

        // encrypted data
        byte encryptedData[] = parcelCrypto.cloakData(parcel);

        stream.write(encryptedData, 0, encryptedData.length);

        byte[] pack = packageData.toByteArray();
        if (childWriter != null)
            return childWriter.pack(pack);
        return pack;
    }
}
