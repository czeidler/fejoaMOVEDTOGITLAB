package org.fejoa.library;

import org.fejoa.library.crypto.*;
import org.fejoa.library.support.PositionInputStream;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;


class ParcelCrypto {
    private ICryptoInterface crypto = Crypto.get();
    final private byte iv[];
    final private SecretKey key;

    public ParcelCrypto() throws CryptoException {
        iv = crypto.generateInitializationVector(CryptoSettings.SYMMETRIC_KEY_IV_SIZE);
        key = crypto.generateSymmetricKey(CryptoSettings.SYMMETRIC_KEY_SIZE);
    }

    public ParcelCrypto(ContactPrivate owner, KeyId keyId, byte iv[], byte encryptedSymmetricKey[]) throws Exception {
        this.iv = iv;

        PrivateKey privateKey = owner.getKeyPair(keyId.getKeyId()).getPrivate();
        byte decryptedSymmetricKey[] = crypto.decryptAsymmetric(encryptedSymmetricKey, privateKey);
        key = CryptoHelper.symmetricKeyFromRaw(decryptedSymmetricKey);
    }

    public ParcelCrypto(ParcelCrypto parcelCrypto)
    {
        this.iv = parcelCrypto.iv;
        this.key = parcelCrypto.key;
    }

    public byte[] cloakData(byte data[]) throws Exception {
        return crypto.encryptSymmetric(data, key, iv);
    }

    public byte[] uncloakData(byte cloakedData[]) throws Exception {
        return crypto.decryptSymmetric(cloakedData, key, iv);
    }

    public byte[] getIV() {
        return iv;
    }

    public SecretKey getSymmetricKey() {
        return key;
    }

    public byte[] getEncryptedSymmetricKey(ContactPublic receiver, KeyId keyId) throws Exception {
        PublicKey publicKey = receiver.getKey(keyId.getKeyId());
        return crypto.encryptAsymmetric(key.getEncoded(), publicKey);
    }
}


interface IParcelEnvelopeWriter {
    public byte[] pack(byte data[]) throws Exception;
}

interface IParcelEnvelopeReader {
    public byte[] unpack(byte parcel[]) throws Exception;
}

class SignatureEnvelopeWriter implements IParcelEnvelopeWriter {
    private String uid;
    private ContactPrivate sender;
    private KeyId signatureKey;
    private byte signature[];
    private IParcelEnvelopeWriter childWriter;

    public SignatureEnvelopeWriter(ContactPrivate sender, KeyId signatureKey, IParcelEnvelopeWriter childWriter) {
        this.sender = sender;
        this.signatureKey = signatureKey;
    }

    /**
     * The uid is calculated in pack. The uid is the sha hash of the packed data. Note, that this might be different
     * to the sha hash of the data argument in pack since the written data can come from the childWriter.
     *
     * @return the uid (pack needed to be called)
     */
    public String getUid() {
        return uid;
    }

    @Override
    public byte[] pack(byte[] data) throws Exception {
        /*
         Structure:
         - signature length int
         - signature
         -- sender uid \n
         -- signature key id \n
         -- data length int
         -- data
         */
        ByteArrayOutputStream packageData = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(packageData);
        stream.writeBytes(sender.getUid() + "\n");
        stream.writeBytes(signatureKey + "\n");

        // write the data
        byte outData[] = data;
        if (childWriter != null)
            outData = childWriter.pack(data);
        uid = CryptoHelper.toHex(CryptoHelper.sha256Hash(outData));

        stream.writeInt(outData.length);
        stream.write(outData, 0, outData.length);

        // signature
        String signatureHash = CryptoHelper.toHex(CryptoHelper.sha1Hash(packageData.toByteArray()));
        signature = sender.sign(signatureKey, signatureHash.getBytes());

        ByteArrayOutputStream outDataStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(packageData);
        out.writeInt(signature.length);
        out.write(signature, 0, signature.length);

        return outDataStream.toByteArray();
    }
}

class SignatureEnvelopeReader implements IParcelEnvelopeReader {
    private String uid;
    private IPublicContactFinder contactFinder;
    private IParcelEnvelopeReader childReader;

    public SignatureEnvelopeReader(IPublicContactFinder contactFinder, IParcelEnvelopeReader childReader) {
        this.contactFinder = contactFinder;
        this.childReader = childReader;
    }

    public String getUid() {
        return uid;
    }

    @Override
    public byte[] unpack(byte[] parcel) throws Exception {
        PositionInputStream positionInputStream = new PositionInputStream(new ByteArrayInputStream(parcel));
        DataInputStream stream = new DataInputStream(positionInputStream);

        int signatureLength = stream.readInt();
        byte signature[] = new byte[signatureLength];
        stream.readFully(signature, 0, signatureLength);


        int position = (int)positionInputStream.getPosition();
        byte signedData[] = new byte[parcel.length - position];
        stream.readFully(signedData, 0, signedData.length);
        String signatureHash = CryptoHelper.toHex(CryptoHelper.sha256Hash(signedData));

        // read from signed data
        DataInputStream signedDataStream = new DataInputStream(new ByteArrayInputStream(signedData));

        String senderUid = readLine(signedDataStream);
        KeyId signatureKey = new KeyId(readLine(signedDataStream));

        ContactPublic sender = contactFinder.find(senderUid);
        if (sender == null)
            throw new Exception("contact not found");

        int mainDataLength = signedDataStream.readInt();
        byte data[] = new byte[mainDataLength];
        signedDataStream.readFully(data, 0, mainDataLength);
        uid = CryptoHelper.toHex(CryptoHelper.sha1Hash(data));

        // validate data
        if (!sender.verify(signatureKey, signatureHash.getBytes(), signature))
            throw new Exception("can't be verified");

        if (childReader != null)
            return childReader.unpack(data);
        return data;
    }

    static public String readLine(InputStream stream) throws IOException {
        String string = "";
        byte c[] = new byte[1];
        while (true) {
            stream.read(c, 0, 1);
            if (c[0] == '\n')
                break;
            string += c;
        }
        return string;
    }
}

class SecureEnvelopeWriter implements IParcelEnvelopeWriter{
    private ContactPublic receiver;
    private KeyId asymmetricKeyId;
    private ParcelCrypto parcelCrypto;
    private IParcelEnvelopeWriter childWriter;

    public SecureEnvelopeWriter(ContactPublic receiver, KeyId asymmetricKeyId, IParcelEnvelopeWriter childWriter)
            throws CryptoException {
        this.receiver = receiver;
        this.asymmetricKeyId = asymmetricKeyId;
        this.childWriter = childWriter;
        parcelCrypto = new ParcelCrypto();
    }

    public SecureEnvelopeWriter(ContactPublic receiver, KeyId asymmetricKeyId, ParcelCrypto parcelCrypto,
                                IParcelEnvelopeWriter childWriter) {
        this.receiver = receiver;
        this.asymmetricKeyId = asymmetricKeyId;
        this.parcelCrypto = parcelCrypto;
        this.childWriter = childWriter;
    }

    @Override
    public byte[] pack(byte[] data) throws Exception {
        byte encryptedSymmetricKey[] = parcelCrypto.getEncryptedSymmetricKey(receiver, asymmetricKeyId);

        ByteArrayOutputStream packageData = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(packageData);

        // asym key id
        stream.writeBytes(asymmetricKeyId.getKeyId() + "\n");

        // encrypted sym key
        stream.writeInt(encryptedSymmetricKey.length);
        stream.write(encryptedSymmetricKey, 0, encryptedSymmetricKey.length);

        // iv
        byte iv[] = parcelCrypto.getIV();
        stream.writeInt(iv.length);
        stream.write(iv, 0, iv.length);

        // encrypted data
        byte outData[] = data;
        if (childWriter != null)
            outData = childWriter.pack(data);
        byte encryptedData[] = parcelCrypto.cloakData(outData);

        stream.writeInt(encryptedData.length);
        stream.write(encryptedData, 0, encryptedData.length);

        return packageData.toByteArray();
    }
}

class SecureEnvelopeReader implements IParcelEnvelopeReader {
    private ContactPrivate owner;
    private IParcelEnvelopeReader childReader;

    public SecureEnvelopeReader(ContactPrivate owner, IParcelEnvelopeReader childReader) {
        this.owner = owner;
        this.childReader = childReader;
    }

    @Override
    public byte[] unpack(byte[] parcel) throws Exception {
        PositionInputStream positionInputStream = new PositionInputStream(new ByteArrayInputStream(parcel));
        DataInputStream stream = new DataInputStream(positionInputStream);

        // asym key id
        KeyId asymmetricKeyId = new KeyId(SignatureEnvelopeReader.readLine(stream));

        // encrypted sym key
        int length = stream.readInt();
        byte[] encryptedSymmetricKey = new byte[length];
        stream.readFully(encryptedSymmetricKey, 0, encryptedSymmetricKey.length);

        // iv
        length = stream.readInt();
        byte[] iv = new byte[length];
        stream.readFully(iv, 0, iv.length);

        // encrypted data
        int position = (int)positionInputStream.getPosition();
        byte encryptedData[] = new byte[parcel.length - position];
        stream.readFully(encryptedData, 0, encryptedData.length);

        ParcelCrypto parcelCrypto = new ParcelCrypto(owner, asymmetricKeyId, iv, encryptedSymmetricKey);
        byte data[] = parcelCrypto.uncloakData(encryptedData);
        if (childReader != null)
            return childReader.unpack(data);

        return data;
    }
}
