/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import org.fejoa.library.Constants;
import org.json.JSONException;
import org.json.JSONObject;


public class JsonCryptoSettings {
    static public JSONObject toJson(CryptoSettings.KeyTypeSettings keyTypeSettings) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(Constants.KEY_SIZE_KEY, keyTypeSettings.keySize);
        object.put(Constants.KEY_TYPE_KEY, keyTypeSettings.keyType);
        return object;
    }

    static public CryptoSettings.KeyTypeSettings keyTypeFromJson(JSONObject object) throws JSONException {
        CryptoSettings.KeyTypeSettings settings = new CryptoSettings.KeyTypeSettings();
        settings.keySize = object.getInt(Constants.KEY_SIZE_KEY);
        settings.keyType = object.getString(Constants.KEY_TYPE_KEY);
        return settings;
    }

    static public JSONObject toJson(CryptoSettings.Signature settings) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(Constants.KEY_SIZE_KEY, settings.keySize);
        object.put(Constants.KEY_TYPE_KEY, settings.keyType);
        object.put(Constants.ALGORITHM_KEY, settings.algorithm);
        return object;
    }

    static public CryptoSettings.Signature signatureFromJson(JSONObject object) throws JSONException {
        CryptoSettings.Signature settings = new CryptoSettings.Signature();
        settings.keySize = object.getInt(Constants.KEY_SIZE_KEY);
        settings.keyType = object.getString(Constants.KEY_TYPE_KEY);
        settings.algorithm = object.getString(Constants.ALGORITHM_KEY);
        return settings;
    }

    static public JSONObject toJson(CryptoSettings.Asymmetric settings) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(Constants.KEY_SIZE_KEY, settings.keySize);
        object.put(Constants.KEY_TYPE_KEY, settings.keyType);
        object.put(Constants.ALGORITHM_KEY, settings.algorithm);
        return object;
    }

    static public CryptoSettings.Asymmetric asymFromJson(JSONObject object) throws JSONException {
        CryptoSettings.Asymmetric settings = new CryptoSettings.Asymmetric();
        settings.keySize = object.getInt(Constants.KEY_SIZE_KEY);
        settings.keyType = object.getString(Constants.KEY_TYPE_KEY);
        settings.algorithm = object.getString(Constants.ALGORITHM_KEY);
        return settings;
    }

    static public JSONObject toJson(CryptoSettings.Symmetric settings) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(Constants.KEY_SIZE_KEY, settings.keySize);
        object.put(Constants.KEY_TYPE_KEY, settings.keyType);
        object.put(Constants.ALGORITHM_KEY, settings.algorithm);
        object.put(Constants.IV_SIZE_KEY, settings.ivSize);
        return object;
    }

    static public CryptoSettings.Symmetric symFromJson(JSONObject object) throws JSONException {
        CryptoSettings.Symmetric settings = new CryptoSettings.Symmetric();
        settings.keySize = object.getInt(Constants.KEY_SIZE_KEY);
        settings.keyType = object.getString(Constants.KEY_TYPE_KEY);
        settings.algorithm = object.getString(Constants.ALGORITHM_KEY);
        settings.ivSize = object.getInt(Constants.IV_SIZE_KEY);
        return settings;
    }
}
