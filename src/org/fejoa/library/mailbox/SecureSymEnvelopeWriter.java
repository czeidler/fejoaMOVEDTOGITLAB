/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;


public class SecureSymEnvelopeWriter implements IParcelEnvelopeWriter {
   private ParcelCrypto parcelCrypto;
    private IParcelEnvelopeWriter childWriter;

    public SecureSymEnvelopeWriter(ParcelCrypto parcelCrypto, IParcelEnvelopeWriter childWriter)
            throws CryptoException {
        this.childWriter = childWriter;
        this.parcelCrypto = parcelCrypto;
    }

    @Override
    public byte[] pack(byte[] parcel) throws CryptoException, IOException {
        // encrypted data
        byte encryptedData[] = parcelCrypto.cloakData(parcel);

        if (childWriter != null)
            return childWriter.pack(encryptedData);
        return encryptedData;
    }
}
