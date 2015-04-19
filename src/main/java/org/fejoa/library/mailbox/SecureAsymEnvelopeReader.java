/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.support.PositionInputStream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.PrivateKey;


public class SecureAsymEnvelopeReader implements IParcelEnvelopeReader {
    final ContactPrivate owner;
    ParcelCrypto parcelCrypto;
    final CryptoSettings asymSettings;
    final IParcelEnvelopeReader childReader;

    public SecureAsymEnvelopeReader(ContactPrivate owner, CryptoSettings asymSettings,
                                    IParcelEnvelopeReader childReader) {
        this.owner = owner;
        this.asymSettings = asymSettings;
        this.childReader = childReader;
    }

    public ParcelCrypto getParcelCrypto() {
        return parcelCrypto;
    }

    private byte[] decryptAsym(byte[] data, String keyId) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        PrivateKey privateKey = owner.getKeyPair(keyId).getPrivate();
        return crypto.decryptAsymmetric(data, privateKey, asymSettings);
    }

    @Override
    public byte[] unpack(byte[] parcel) throws IOException, CryptoException {
        PositionInputStream positionInputStream = new PositionInputStream(new ByteArrayInputStream(parcel));
        DataInputStream stream = new DataInputStream(positionInputStream);

        // asym algorithm and key id
        asymSettings.asymmetricAlgorithm = stream.readLine();
        String asymmetricKeyId = stream.readLine();

        // sym key type
        int length = stream.readInt();
        byte[] keyTypeEncBuffer = new byte[length];
        stream.readFully(keyTypeEncBuffer, 0, keyTypeEncBuffer.length);

        // sym algo
        length = stream.readInt();
        byte[] symAlgoEncBuffer = new byte[length];
        stream.readFully(symAlgoEncBuffer, 0, symAlgoEncBuffer.length);

        // encrypted sym key
        length = stream.readInt();
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

        CryptoSettings symSettings = CryptoSettings.symmetricSettings(
                new String(decryptAsym(keyTypeEncBuffer, asymmetricKeyId)),
                new String(decryptAsym(symAlgoEncBuffer, asymmetricKeyId)));
        parcelCrypto = new ParcelCrypto(iv, decryptAsym(encryptedSymmetricKey, asymmetricKeyId), symSettings);

        byte data[] = parcelCrypto.uncloakData(encryptedData);
        if (childReader != null)
            return childReader.unpack(data);

        return data;
    }
}
