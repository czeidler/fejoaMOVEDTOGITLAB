/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


class OutgoingSecureChannel {
    private ContactPublic receiver;

    private String parentChannel = "";
    private KeyId asymmetricKeyId;
    private ParcelCrypto parcelCrypto;

    // outgoing
    public OutgoingSecureChannel(ContactPublic receiver, KeyId asymKeyId) throws CryptoException {
        this.receiver = receiver;
        this.asymmetricKeyId = asymKeyId;
        this.parcelCrypto = new ParcelCrypto();
    }

    public void writeMainData(DataOutputStream stream) throws CryptoException, IOException {
        byte[] encryptedSymmetricKey = parcelCrypto.getEncryptedSymmetricKey(receiver, asymmetricKeyId);

        stream.writeBytes(asymmetricKeyId.getKeyId());
        stream.write('\n');
        stream.writeInt(encryptedSymmetricKey.length);
        stream.write(encryptedSymmetricKey);
        stream.writeInt(parcelCrypto.getIV().length);
        stream.write(parcelCrypto.getIV());

        stream.writeBytes(parentChannel);
        stream.write('\n');
    }
}


class IncomingSecureChannel  {
    private ContactPrivate owner;

    private String parentChannel = "";
    private KeyId asymmetricKeyId;
    private ParcelCrypto parcelCrypto;

    // incoming
    public IncomingSecureChannel(ContactPrivate owner) throws CryptoException {
        this.owner = owner;

    }

    public void readMainData(DataInputStream stream, KeyStore keyStore) throws IOException, CryptoException {
        asymmetricKeyId = new KeyId(stream.readLine());
        int encryptedSize = stream.readInt();
        byte[] encryptedSymmetricKey = new byte[encryptedSize];
        stream.readFully(encryptedSymmetricKey);

        int ivSize = stream.readInt();
        byte[] iv = new byte[ivSize];
        stream.readFully(iv);

        this.parentChannel = stream.readLine();

        this.parcelCrypto = new ParcelCrypto(owner, asymmetricKeyId, iv, encryptedSymmetricKey);
    }
};

