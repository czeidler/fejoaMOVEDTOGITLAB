package org.fejoa.library.mailbox;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.PositionInputStream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class SecureAsymEnvelopeReader implements IParcelEnvelopeReader {
    private ContactPrivate owner;
    private ParcelCrypto parcelCrypto;
    private IParcelEnvelopeReader childReader;

    public SecureAsymEnvelopeReader(ContactPrivate owner, IParcelEnvelopeReader childReader) {
        this.owner = owner;
        this.childReader = childReader;
    }

    public ParcelCrypto getParcelCrypto() {
        return parcelCrypto;
    }

    @Override
    public byte[] unpack(byte[] parcel) throws IOException, CryptoException {
        PositionInputStream positionInputStream = new PositionInputStream(new ByteArrayInputStream(parcel));
        DataInputStream stream = new DataInputStream(positionInputStream);

        // asym key id
        KeyId asymmetricKeyId = new KeyId(stream.readLine());

        // encrypted sym key
        int length = stream.readInt();
        byte[] encryptedSymmetricKey = new byte[length];
        stream.readFully(encryptedSymmetricKey, 0, encryptedSymmetricKey.length);

        // iv
        length = stream.readInt();
        byte[] iv = new byte[length];
        stream.readFully(iv, 0, iv.length);

        // encrypted data
        int position = (int)positionInputStream.getPosition();
        byte encryptedData[] = new byte[parcel.length - position];
        stream.readFully(encryptedData, 0, encryptedData.length);

        parcelCrypto = new ParcelCrypto(owner, asymmetricKeyId, iv, encryptedSymmetricKey);
        byte data[] = parcelCrypto.uncloakData(encryptedData);
        if (childReader != null)
            return childReader.unpack(data);

        return data;
    }
}
