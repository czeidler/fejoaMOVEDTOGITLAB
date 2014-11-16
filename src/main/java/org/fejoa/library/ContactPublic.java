/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.ICryptoInterface;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ContactPublic extends Contact {
    protected Map<String, KeyPair> keys = new HashMap<>();

    // open contact
    public ContactPublic(SecureStorageDir storageDir) throws IOException, CryptoException {
        super(storageDir);

        open();
    }

    // new contact
    public ContactPublic(SecureStorageDir storageDir, String uid) {
        super(storageDir);

        this.uid = uid;
    }

    public String getUid() {
        return uid;
    }

    @Override
    public void write() throws IOException, CryptoException {
        super.write();

        for (Map.Entry<String, KeyPair> entry : keys.entrySet()) {
            KeyPair keyPair = entry.getValue();
            String keyId = entry.getKey();
            storageDir.writeString("keys/" + keyId + "/publicKey", CryptoHelper.convertToPEM(keyPair.getPublic()));
        }
    }

    @Override
    public void open() throws IOException, CryptoException {
        super.open();

        List<String> keyIds = storageDir.listDirectories("keys");
        for (String keyId : keyIds) {
            PublicKey publicKey = CryptoHelper.publicKeyFromPem(storageDir.readString("keys/" + keyId + "/publicKey"));
            keys.put(keyId, new KeyPair(publicKey, null));
        }
    }

    public PublicKey getPublicKey(KeyId keyId) {
        return keys.get(keyId.getKeyId()).getPublic();
    }

    public void addKey(String keyId, PublicKey publicKey) {
        keys.put(keyId, new KeyPair(publicKey, null));
    }

    @Override
    public boolean verify(KeyId keyId, byte data[], byte signature[]) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        PublicKey publicKey = getPublicKey(keyId);
        return crypto.verifySignature(data, signature, publicKey);
    }
}
