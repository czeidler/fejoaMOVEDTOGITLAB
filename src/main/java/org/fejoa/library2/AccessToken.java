/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library.crypto.*;
import org.fejoa.library2.database.StorageDir;
import org.fejoa.library2.util.CryptoSettingsIO;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;


public class AccessToken implements IStorageDirBundle {
    final static public String CONTACT_AUTH_KEY_JSON_SETTINGS_KEY = "contactAuthKeySettings";
    final static public String CONTACT_AUTH_KEY_SETTINGS_KEY = "contactAuthKey";
    final static public String CONTACT_AUTH_PUBLIC_KEY_KEY = "contactAuthPublicKey";
    final static public String CONTACT_AUTH_PRIVATE_KEY_KEY = "contactAuthPrivateKey";
    final static public String SIGNATURE_KEY_SETTINGS_KEY = "accessSignatureKey";
    final static public String SIGNATURE_VERIFICATION_KEY_KEY = "signatureVerificationKey";
    final static private String SIGNATURE_SIGNING_KEY_KEY = "signatureSigningKey";
    final static public String ACCESS_ENTRY_KEY = "accessEntry";
    final static public String ACCESS_ENTRY_SIGNATURE_KEY = "accessEntrySignature";

    final private FejoaContext context;
    private CryptoSettings.Signature contactAuthKeySettings;
    private KeyPair contactAuthKey;
    private CryptoSettings.Signature accessSignatureKeySettings;
    private KeyPair accessSignatureKey;
    private String accessEntry;

    final private List<AccessContact> contacts = new ArrayList<>();

    static public AccessToken create(FejoaContext context) throws CryptoException {
        return new AccessToken(context);
    }

    static public AccessToken open(FejoaContext context, StorageDir storageDir) throws IOException {
        return new AccessToken(context, storageDir);
    }

    private AccessToken(FejoaContext context) throws CryptoException {
        this.context = context;

        ICryptoInterface crypto = context.getCrypto();
        contactAuthKeySettings = context.getCryptoSettings().signature;
        contactAuthKey = crypto.generateKeyPair(contactAuthKeySettings);

        accessSignatureKeySettings = context.getCryptoSettings().signature;
        accessSignatureKey = crypto.generateKeyPair(accessSignatureKeySettings);
    }

    private AccessToken(FejoaContext context, StorageDir storageDir) throws IOException {
        this.context = context;

        contactAuthKeySettings = new CryptoSettings.Signature();
        accessSignatureKeySettings = new CryptoSettings.Signature();

        read(storageDir);
    }

    public String getAccessEntry() {
        return accessEntry;
    }

    public void setAccessEntry(String accessEntry) {
        this.accessEntry = accessEntry;
    }

    public byte[] getAccessEntrySignature() throws CryptoException {
        return context.getCrypto().sign(accessEntry.getBytes(), accessSignatureKey.getPrivate(),
                accessSignatureKeySettings);
    }

    public String getId() {
        return CryptoHelper.sha1HashHex(contactAuthKey.getPublic().getEncoded());
    }

    public JSONObject getContactToken() throws JSONException, CryptoException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.ID_KEY, getId());
        jsonObject.put(ACCESS_ENTRY_SIGNATURE_KEY, DatatypeConverter.printBase64Binary(getAccessEntrySignature()));
        jsonObject.put(ACCESS_ENTRY_KEY, accessEntry);
        jsonObject.put(CONTACT_AUTH_KEY_JSON_SETTINGS_KEY, JsonCryptoSettings.toJson(contactAuthKeySettings));
        jsonObject.put(CONTACT_AUTH_PRIVATE_KEY_KEY, DatatypeConverter.printBase64Binary(
                contactAuthKey.getPrivate().getEncoded()));
        return jsonObject;
    }

    @Override
    public void write(StorageDir dir) throws IOException {
        // the public keys must be readable by the server
        StorageDir plainDir = new StorageDir(dir);
        plainDir.setFilter(null);

        CryptoSettingsIO.write(contactAuthKeySettings, plainDir, CONTACT_AUTH_KEY_SETTINGS_KEY);
        plainDir.writeBytes(CONTACT_AUTH_PUBLIC_KEY_KEY, contactAuthKey.getPublic().getEncoded());
        dir.writeBytes(CONTACT_AUTH_PRIVATE_KEY_KEY, contactAuthKey.getPrivate().getEncoded());

        CryptoSettingsIO.write(accessSignatureKeySettings, plainDir, SIGNATURE_KEY_SETTINGS_KEY);
        plainDir.writeBytes(SIGNATURE_VERIFICATION_KEY_KEY, accessSignatureKey.getPublic().getEncoded());
        dir.writeBytes(SIGNATURE_SIGNING_KEY_KEY, accessSignatureKey.getPrivate().getEncoded());

        dir.writeString(ACCESS_ENTRY_KEY, accessEntry);

        // write contacts
        StorageDir contactBaseDir = new StorageDir(dir, "contacts");
        for (AccessContact contact : contacts) {
            StorageDir contactDir = new StorageDir(contactBaseDir, contact.getContact());
            contact.write(contactDir);
        }
    }

    @Override
    public void read(StorageDir dir) throws IOException {
        // the public keys must be readable by the server
        StorageDir plainDir = new StorageDir(dir);
        plainDir.setFilter(null);

        CryptoSettingsIO.read(contactAuthKeySettings, plainDir, CONTACT_AUTH_KEY_SETTINGS_KEY);
        PrivateKey privateKey;
        PublicKey publicKey;
        try {
            publicKey = CryptoHelper.publicKeyFromRaw(plainDir.readBytes(CONTACT_AUTH_PUBLIC_KEY_KEY),
                    contactAuthKeySettings.keyType);
            privateKey = CryptoHelper.privateKeyFromRaw(dir.readBytes(CONTACT_AUTH_PRIVATE_KEY_KEY),
                    contactAuthKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        contactAuthKey = new KeyPair(publicKey, privateKey);

        CryptoSettingsIO.read(accessSignatureKeySettings, plainDir, SIGNATURE_KEY_SETTINGS_KEY);
        try {
            publicKey = CryptoHelper.publicKeyFromRaw(plainDir.readBytes(SIGNATURE_VERIFICATION_KEY_KEY),
                    accessSignatureKeySettings.keyType);
            privateKey = CryptoHelper.privateKeyFromRaw(dir.readBytes(SIGNATURE_SIGNING_KEY_KEY),
                    accessSignatureKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        accessSignatureKey = new KeyPair(publicKey, privateKey);

        accessEntry = dir.readString(ACCESS_ENTRY_KEY);

        // read contacts
        List<String> dirs = dir.listDirectories("contacts");
        for (String subDir : dirs) {
            StorageDir contactDir = new StorageDir(dir, "contacts/" + subDir);
            AccessContact accessContact = new AccessContact();
            accessContact.read(contactDir);
            contacts.add(accessContact);
        }
    }
}
