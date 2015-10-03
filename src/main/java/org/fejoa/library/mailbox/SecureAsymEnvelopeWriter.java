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
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.PublicKey;


public class SecureAsymEnvelopeWriter implements IParcelEnvelopeWriter{
    final Contact receiver;
    final KeyId asymmetricKeyId;
    final ParcelCrypto parcelCrypto;
    final IParcelEnvelopeWriter childWriter;
    final CryptoSettings.Asymmetric asymSettings;

    public SecureAsymEnvelopeWriter(Contact receiver, KeyId asymmetricKeyId, ParcelCrypto parcelCrypto,
                                    CryptoSettings.Asymmetric asymSettings,
                                    IParcelEnvelopeWriter childWriter) {
        this.receiver = receiver;
        this.asymmetricKeyId = asymmetricKeyId;
        this.parcelCrypto = parcelCrypto;
        this.asymSettings = asymSettings;
        this.childWriter = childWriter;
    }

    public ParcelCrypto getParcelCrypto() {
        return parcelCrypto;
    }

    private byte[] encrypteAsym(byte[] data) throws CryptoException {
        PublicKey publicKey = receiver.getPublicKey(asymmetricKeyId);
        ICryptoInterface crypto = Crypto.get();
        return crypto.encryptAsymmetric(data, publicKey, asymSettings);
    }

    @Override
    public byte[] pack(byte[] parcel) throws CryptoException, IOException {
        ByteArrayOutputStream packageData = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(packageData);

        CryptoSettings.Symmetric settings = parcelCrypto.getSymmetricSettings();
        // asym kdfAlgorithm and key id
        stream.writeBytes(settings.algorithm + "\n");
        stream.writeBytes(asymmetricKeyId.getKeyId() + "\n");

        // encrypted sym key type
        byte[] encryptedSymKeyType = encrypteAsym(settings.keyType.getBytes());
        stream.writeInt(encryptedSymKeyType.length);
        stream.write(encryptedSymKeyType, 0, encryptedSymKeyType.length);
        // encrypted sym kdfAlgorithm
        byte[] encryptedSymAlgorithm = encrypteAsym(settings.algorithm.getBytes());
        stream.writeInt(encryptedSymAlgorithm.length);
        stream.write(encryptedSymAlgorithm, 0, encryptedSymAlgorithm.length);
        // encrypted sym key
        byte encryptedSymmetricKey[] = encrypteAsym(parcelCrypto.getSymmetricKey().getEncoded());
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
