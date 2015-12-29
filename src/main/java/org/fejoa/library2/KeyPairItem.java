/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library2.database.StorageDir;
import org.fejoa.library2.util.CryptoSettingsIO;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;


public class KeyPairItem implements IStorageDirBundle {
    final private String PATH_PRIVATE_KEY = "privateKey";
    final private String PATH_PUBLIC_KEY = "publicKey";

    private String id;
    private KeyPair keyPair;
    private CryptoSettings.KeyTypeSettings typeSettings;

    KeyPairItem() {
        typeSettings = new CryptoSettings.KeyTypeSettings();
    }

    public KeyPairItem(KeyPair keyPair, CryptoSettings.KeyTypeSettings typeSettings) {
        this.id = CryptoHelper.sha1HashHex(keyPair.getPublic().getEncoded());
        this.keyPair = keyPair;
        this.typeSettings = typeSettings;
    }

    @Override
    public void write(StorageDir dir) throws IOException {
        dir.writeString(Constants.ID_KEY, id);
        dir.writeBytes(PATH_PRIVATE_KEY, keyPair.getPrivate().getEncoded());
        dir.writeBytes(PATH_PUBLIC_KEY, keyPair.getPublic().getEncoded());
        CryptoSettingsIO.write(typeSettings, dir, "");
    }

    @Override
    public void read(StorageDir dir) throws IOException {
        id = dir.readString(Constants.ID_KEY);
        CryptoSettingsIO.read(typeSettings, dir, "");
        PrivateKey privateKey;
        PublicKey publicKey;
        try {
            privateKey = CryptoHelper.privateKeyFromRaw(dir.readBytes(PATH_PRIVATE_KEY), typeSettings.keyType);
            publicKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(PATH_PUBLIC_KEY), typeSettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        keyPair = new KeyPair(publicKey, privateKey);
    }

    public String getId() {
        return id;
    }

    public KeyId getKeyId() {
        return new KeyId(id);
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public CryptoSettings.KeyTypeSettings getKeyTypeSettings() {
        return typeSettings;
    }
}
