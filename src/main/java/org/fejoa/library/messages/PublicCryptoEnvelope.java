/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.messages;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.*;
import org.fejoa.library.support.StreamHelper;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.KeyPairItem;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.security.PublicKey;


public class PublicCryptoEnvelope {
    static final public String CRYPTO_TYPE = "pub";
    static final public String PUBLIC_KEY_ID_KEY = "keyId";
    static final public String IV_KEY = "iv";
    static final public String ENC_SYMMETRIC_KEY_KEY = "symKey";
    static final public String SYMMETRIC_SETTINGS_KEY = "symSettings";
    static final public String PUBLIC_KEY_SETTINGS_KEY = "pubKeySettings";

    static public InputStream encrypt(InputStream data, boolean isRawData, KeyId keyId, PublicKey key,
                                      FejoaContext context) throws JSONException, CryptoException, IOException {
        JSONObject object = new JSONObject();
        object.put(Envelope.PACK_TYPE_KEY, CRYPTO_TYPE);
        if (isRawData)
            object.put(Envelope.CONTAINS_DATA_KEY, 1);

        ICryptoInterface crypto = context.getCrypto();
        CryptoSettings.Asymmetric pubKeySettings = context.getCryptoSettings().publicKey;
        CryptoSettings.Symmetric symSettings = context.getCryptoSettings().symmetric;
        byte[] iv = crypto.generateInitializationVector(symSettings.ivSize);
        String base64IV = DatatypeConverter.printBase64Binary(iv);
        SecretKey symKey = crypto.generateSymmetricKey(symSettings);

        // encrypt the key
        byte[] encSymKey = crypto.encryptAsymmetric(symKey.getEncoded(), key, pubKeySettings);
        String base64EncSymKey = DatatypeConverter.printBase64Binary(encSymKey);

        object.put(PUBLIC_KEY_ID_KEY, keyId.getKeyId());
        object.put(PUBLIC_KEY_SETTINGS_KEY, JsonCryptoSettings.toJson(pubKeySettings));
        object.put(IV_KEY, base64IV);
        object.put(ENC_SYMMETRIC_KEY_KEY, base64EncSymKey);
        object.put(SYMMETRIC_SETTINGS_KEY, JsonCryptoSettings.toJson(symSettings));
        String header = object.toString() + "\n";

        InputStream crytoStream = crypto.encryptSymmetric(data, symKey, iv, symSettings);
        return new SequenceInputStream(new ByteArrayInputStream(header.getBytes()), crytoStream);
    }

    static public InputStream decryptStream(JSONObject header, InputStream inputStream, ContactPrivate contact,
                                            FejoaContext context)
            throws JSONException, IOException, CryptoException {
        String keyId = header.getString(PUBLIC_KEY_ID_KEY);
        CryptoSettings.Asymmetric asymSettings = JsonCryptoSettings.asymFromJson(header.getJSONObject(
                PUBLIC_KEY_SETTINGS_KEY));
        byte[] iv = DatatypeConverter.parseBase64Binary(header.getString(IV_KEY));
        byte[] encSymKey = DatatypeConverter.parseBase64Binary(header.getString(ENC_SYMMETRIC_KEY_KEY));
        CryptoSettings.Symmetric symSettings = JsonCryptoSettings.symFromJson(header.getJSONObject(
                SYMMETRIC_SETTINGS_KEY));

        ICryptoInterface crypto = context.getCrypto();
        KeyPairItem keyPairItem = contact.getEncryptionKey(keyId);
        if (keyPairItem == null)
            throw new IOException("key not found");
        byte[] symKey = crypto.decryptAsymmetric(encSymKey,keyPairItem.getKeyPair().getPrivate(), asymSettings);
        SecretKey key;
        try {
            key = CryptoHelper.secretKey(symKey, symSettings);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        return crypto.decryptSymmetric(inputStream, key, iv, symSettings);
    }

    static public byte[] encrypt(byte[] data, boolean isRawData, KeyId keyId, PublicKey key, FejoaContext context)
            throws JSONException, CryptoException, IOException {
        JSONObject object = new JSONObject();
        object.put(Envelope.PACK_TYPE_KEY, CRYPTO_TYPE);
        if (isRawData)
            object.put(Envelope.CONTAINS_DATA_KEY, 1);

        ICryptoInterface crypto = context.getCrypto();
        CryptoSettings.Asymmetric pubKeySettings = context.getCryptoSettings().publicKey;
        CryptoSettings.Symmetric symSettings = context.getCryptoSettings().symmetric;
        byte[] iv = crypto.generateInitializationVector(symSettings.ivSize);
        String base64IV = DatatypeConverter.printBase64Binary(iv);
        SecretKey symKey = crypto.generateSymmetricKey(symSettings);

        // encrypt the key
        byte[] encSymKey = crypto.encryptAsymmetric(symKey.getEncoded(), key, pubKeySettings);
        String base64EncSymKey = DatatypeConverter.printBase64Binary(encSymKey);

        object.put(PUBLIC_KEY_ID_KEY, keyId.getKeyId());
        object.put(PUBLIC_KEY_SETTINGS_KEY, JsonCryptoSettings.toJson(pubKeySettings));
        object.put(IV_KEY, base64IV);
        object.put(ENC_SYMMETRIC_KEY_KEY, base64EncSymKey);
        object.put(SYMMETRIC_SETTINGS_KEY, JsonCryptoSettings.toJson(symSettings));
        String header = object.toString() + "\n";

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        outStream.write(header.getBytes());
        OutputStream crytoStream = crypto.encryptSymmetric(outStream, symKey, iv, symSettings);
        crytoStream.write(data);
        crytoStream.flush();
        return outStream.toByteArray();
    }

    static public byte[] decrypt(JSONObject header, InputStream inputStream, ContactPrivate contact,
                                 FejoaContext context) throws JSONException, IOException, CryptoException {
        String keyId = header.getString(PUBLIC_KEY_ID_KEY);
        CryptoSettings.Asymmetric asymSettings = JsonCryptoSettings.asymFromJson(header.getJSONObject(
                PUBLIC_KEY_SETTINGS_KEY));
        byte[] iv = DatatypeConverter.parseBase64Binary(header.getString(IV_KEY));
        byte[] encSymKey = DatatypeConverter.parseBase64Binary(header.getString(ENC_SYMMETRIC_KEY_KEY));
        CryptoSettings.Symmetric symSettings = JsonCryptoSettings.symFromJson(header.getJSONObject(
                SYMMETRIC_SETTINGS_KEY));

        ICryptoInterface crypto = context.getCrypto();
        KeyPairItem keyPairItem = contact.getEncryptionKey(keyId);
        if (keyPairItem == null)
            throw new IOException("key not found");
        byte[] symKey = crypto.decryptAsymmetric(encSymKey,keyPairItem.getKeyPair().getPrivate(), asymSettings);
        SecretKey key;
        try {
            key = CryptoHelper.secretKey(symKey, symSettings);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream cryptoStream = crypto.decryptSymmetric(inputStream, key, iv, symSettings);
        StreamHelper.copy(cryptoStream, outputStream);
        return outputStream.toByteArray();
    }
}
