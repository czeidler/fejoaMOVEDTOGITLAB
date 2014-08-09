/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.Contact;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.KeyId;
import org.fejoa.library.UserIdentity;
import org.fejoa.library.crypto.*;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.KeyPair;
import java.security.PrivateKey;
/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
import java.security.PublicKey;


public abstract class Channel {
    private String branchName;
    // used to encrypt/decrypt data in the channel
    private ParcelCrypto parcelCrypto;
    // used to verify that user is allowed to get access to the channel data
    private PrivateKey signatureKey;
    protected PublicKey signatureKeyPublic;

    protected Channel() {

    }

    public void load(UserIdentity identity, PublicKey signatureKey, byte[] pack)
            throws IOException, CryptoException {
        signatureKeyPublic = signatureKey;

        ChannelBranchReader channelBranchReader =  new ChannelBranchReader();
        SecureAsymEnvelopeReader secureEnvelopeReader = new SecureAsymEnvelopeReader(identity.getMyself(),
                channelBranchReader);
        SignatureEnvelopeReader signatureReader = new SignatureEnvelopeReader(identity.getContactFinder(),
                secureEnvelopeReader);

        signatureReader.unpack(pack);

        parcelCrypto = secureEnvelopeReader.getParcelCrypto();
    }

    protected void create() throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        byte hashResult[] = CryptoHelper.sha1Hash(crypto.generateInitializationVector(40));
        branchName = CryptoHelper.toHex(hashResult);

        parcelCrypto = new ParcelCrypto();

        KeyPair keyPair = crypto.generateKeyPair(CryptoSettings.ASYMMETRIC_KEY_SIZE);
        signatureKey = keyPair.getPrivate();
        signatureKeyPublic = keyPair.getPublic();
    }

    public String getBranchName() {
        return branchName;
    }

    public ParcelCrypto getParcelCrypto() {
        return parcelCrypto;
    }

    protected byte[] pack(ContactPrivate sender, KeyId senderKey, Contact receiver, KeyId receiverKey)
            throws CryptoException, IOException {

        SignatureEnvelopeWriter signatureEnvelopeWriter
                = new SignatureEnvelopeWriter(sender, senderKey, null);
        SecureAsymEnvelopeWriter asymEnvelopeWriter = new SecureAsymEnvelopeWriter(receiver, receiverKey, parcelCrypto,
                signatureEnvelopeWriter);

        ChannelBranchWriter channelBranchWriter = new ChannelBranchWriter(asymEnvelopeWriter);
        return channelBranchWriter.pack(null);
    }

    class ChannelBranchWriter implements IParcelEnvelopeWriter{
        private IParcelEnvelopeWriter childWriter;

        public ChannelBranchWriter(IParcelEnvelopeWriter childWriter) {
            this.childWriter = childWriter;
        }

        @Override
        public byte[] pack(byte[] parcel) throws IOException, CryptoException {
            ByteArrayOutputStream pack = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(pack);

            stream.writeBytes(branchName + "\n");

            byte[] raw = CryptoHelper.convertToPEM(signatureKey).getBytes();
            stream.writeInt(raw.length);
            stream.write(raw, 0, raw.length);

            byte[] out = pack.toByteArray();
            if (childWriter != null)
                return childWriter.pack(out);
            return out;
        }
    }

    class ChannelBranchReader implements IParcelEnvelopeReader {
        @Override
        public byte[] unpack(byte[] parcel) throws IOException, CryptoException {
            ByteArrayInputStream positionInputStream = new ByteArrayInputStream(parcel);
            DataInputStream stream = new DataInputStream(positionInputStream);

            branchName = stream.readLine();

            // signature key
            int length = stream.readInt();
            byte[] raw = new byte[length];
            stream.readFully(raw, 0, raw.length);
            signatureKey = CryptoHelper.privateKeyFromPem(new String(raw));

            return null;
        }
    }
}
