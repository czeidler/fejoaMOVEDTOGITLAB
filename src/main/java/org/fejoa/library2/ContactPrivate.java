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
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;


public class ContactPrivate extends Contact<KeyPairItem> implements IContactPrivate {
    protected ContactPrivate(FejoaContext context, StorageDir dir) {
        super(context, new StorageDirList.AbstractEntryIO<KeyPairItem>() {
            @Override
            public String getId(KeyPairItem entry) {
                return entry.getId();
            }

            @Override
            public KeyPairItem read(StorageDir dir) throws IOException {
                KeyPairItem keyPairItem = new KeyPairItem();
                keyPairItem.read(dir);
                return keyPairItem;
            }
        }, dir);
    }

    @Override
    public PublicKey getVerificationKey(KeyId keyId) {
        return signatureKeys.get(keyId.toString()).getKeyPair().getPublic();
    }

    @Override
    public byte[] sign(KeyId keyId, byte[] data, CryptoSettings.Signature signatureSettings) throws CryptoException {
        ICryptoInterface crypto = context.getCrypto();
        PrivateKey publicKey = signatureKeys.get(keyId.toString()).getKeyPair().getPrivate();
        return crypto.sign(data, publicKey, signatureSettings);
    }
}
