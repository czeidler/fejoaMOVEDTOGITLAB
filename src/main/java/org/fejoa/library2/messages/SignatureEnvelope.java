package org.fejoa.library2.messages;

import org.apache.commons.io.input.ReaderInputStream;
import org.fejoa.library.Contact;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.support.StreamHelper;
import org.fejoa.library2.Constants;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;


public class SignatureEnvelope {
    static final private String SIGNATURE_KEY = "signature";
    static final private String KEY_ID_KEY = "keyId";
    static final private String SETTINGS_KEY = "settings";

    private JSONObject toJson(CryptoSettings.Signature settings) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(Constants.KEY_SIZE_KEY, settings.keySize);
        object.put(Constants.KEY_TYPE_KEY, settings.keyType);
        object.put(Constants.ALGORITHM_KEY, settings.algorithm);
        return object;
    }

    private CryptoSettings.Signature fromJson(JSONObject object) throws JSONException {
        CryptoSettings.Signature settings = new CryptoSettings.Signature();
        settings.keySize = object.getInt(Constants.KEY_SIZE_KEY);
        settings.keyType = object.getString(Constants.KEY_TYPE_KEY);
        settings.algorithm = object.getString(Constants.ALGORITHM_KEY);
        return settings;
    }

    public byte[] sign(byte[] data, ContactPrivate contactPrivate, KeyId keyId, CryptoSettings.Signature settings)
            throws CryptoException, JSONException, IOException {
        byte[] hash = CryptoHelper.sha256Hash(data);
        String signature = CryptoHelper.toHex(contactPrivate.sign(keyId, hash, settings));
        JSONObject object = new JSONObject();
        object.put(KEY_ID_KEY, keyId.toString());
        object.put(SIGNATURE_KEY, signature);
        object.put(SETTINGS_KEY, toJson(settings));
        String header = object.toString() + "\n";

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        outStream.write(header.getBytes());
        outStream.write(data);
        return outStream.toByteArray();
    }

    public byte[] verify(byte[] pack, Contact contact) throws IOException, JSONException, CryptoException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(pack)));
        String header = bufferedReader.readLine();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        StreamHelper.copy(new ReaderInputStream(bufferedReader), outStream);
        byte[] data = outStream.toByteArray();

        // verify
        JSONObject object = new JSONObject(header);
        KeyId keyId = new KeyId(object.getString(KEY_ID_KEY));
        byte[] signature = CryptoHelper.fromHex(object.getString(SIGNATURE_KEY));
        CryptoSettings.Signature settings = fromJson(object.getJSONObject(SETTINGS_KEY));
        if (!contact.verify(keyId, data, signature, settings))
            throw new IOException("can't verify signature!");

        return data;
    }
}
