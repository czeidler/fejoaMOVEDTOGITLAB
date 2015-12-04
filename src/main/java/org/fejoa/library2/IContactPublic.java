/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;


public interface IContactPublic {
    boolean verify(KeyId keyId, byte[] data, byte[] signature, CryptoSettings.Signature signatureSettings)
            throws CryptoException;
}
