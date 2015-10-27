/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.util;

import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;


public class CryptoSettingsIO {
    final static private String KEY_SIZE_KEY = "keySize";
    final static private String KEY_TYPE = "keyType";

    final static private String ALGORITHM_KEY = "algorithm";
    final static private String IV_SIZE_KEY = "ivSize";

    static public void write(CryptoSettings.Symmetric settings, StorageDir dir) throws IOException {
        write(settings, dir, "");
    }

    static private String key(String prefix, String key) {
        if (prefix.equals(""))
            return key;
        return prefix + Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    static public void write(CryptoSettings.Symmetric settings, StorageDir dir, String keyPrefix) throws IOException {
        write((CryptoSettings.KeyTypeSettings)settings, dir, keyPrefix);
        dir.writeString(key(keyPrefix, ALGORITHM_KEY), settings.algorithm);
        dir.writeInt(key(keyPrefix, IV_SIZE_KEY), settings.ivSize);
    }

    static public void write(CryptoSettings.KeyTypeSettings keyTypeSettings, StorageDir dir, String keyPrefix)
            throws IOException {
        dir.writeInt(key(keyPrefix, KEY_SIZE_KEY), keyTypeSettings.keySize);
        dir.writeString(key(keyPrefix, KEY_TYPE), keyTypeSettings.keyType);
    }

    static public void read(CryptoSettings.Symmetric settings, StorageDir dir) throws IOException {
        read(settings, dir, "");
    }

    static public void read(CryptoSettings.Symmetric settings, StorageDir dir, String keyPrefix) throws IOException {
        read((CryptoSettings.KeyTypeSettings)settings, dir, keyPrefix);
        settings.algorithm = dir.readString(key(keyPrefix, ALGORITHM_KEY));
        settings.ivSize = dir.readInt(key(keyPrefix, IV_SIZE_KEY));
    }

    static public void read(CryptoSettings.KeyTypeSettings keyTypeSettings, StorageDir dir, String keyPrefix)
            throws IOException {
        keyTypeSettings.keySize = dir.readInt(key(keyPrefix, KEY_SIZE_KEY));
        keyTypeSettings.keyType = dir.readString(key(keyPrefix, KEY_TYPE));
    }

}
