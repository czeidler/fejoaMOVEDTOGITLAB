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
import java.security.PublicKey;


public class PublicKeyItem implements IStorageDirBundle {
    final private String PATH_KEY = "key";

    private String id;
    private PublicKey key;
    private CryptoSettings.KeyTypeSettings typeSettings;

    PublicKeyItem() {

    }

    PublicKeyItem(PublicKey key, CryptoSettings.KeyTypeSettings settings) {
        this.id = CryptoHelper.sha1HashHex(key.getEncoded());
        this.key = key;
        this.typeSettings = settings;
    }

    public String getId() {
        return id;
    }

    public KeyId getKeyId() {
        return new KeyId(id);
    }

    @Override
    public void write(StorageDir dir) throws IOException {
        dir.writeString(Constants.ID_KEY, id);
        KeyPairItem.write(key, dir, PATH_KEY);
        CryptoSettingsIO.write(typeSettings, dir, "");
    }

    @Override
    public void read(StorageDir dir) throws IOException {
        id = dir.readString(Constants.ID_KEY);
        key = KeyPairItem.read(dir, PATH_KEY);
        CryptoSettingsIO.read(typeSettings, dir, "");
    }

    public PublicKey getKey() {
        return key;
    }
}
