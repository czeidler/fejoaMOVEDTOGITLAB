package org.fejoa.library.mailbox;

import org.fejoa.library.crypto.CryptoException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class SecureSymEnvelopeReader implements IParcelEnvelopeReader {
    private ParcelCrypto parcelCrypto;
    private IParcelEnvelopeReader childReader;

    public SecureSymEnvelopeReader(ParcelCrypto parcelCrypto, IParcelEnvelopeReader childReader) {
        this.parcelCrypto = parcelCrypto;
        this.childReader = childReader;
    }

    @Override
    public byte[] unpack(byte[] parcel) throws IOException, CryptoException {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(parcel));

        // encrypted data
        byte encryptedData[] = new byte[parcel.length];
        stream.readFully(encryptedData, 0, encryptedData.length);

        byte data[] = parcelCrypto.uncloakData(encryptedData);
        if (childReader != null)
            return childReader.unpack(data);

        return data;
    }
}
