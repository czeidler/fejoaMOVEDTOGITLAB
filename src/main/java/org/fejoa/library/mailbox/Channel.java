/*
 * Copyright 2014-2015.
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

import java.io.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;


public abstract class Channel {
    String branchName;
    // used to encrypt/decrypt data in the channel
    ParcelCrypto parcelCrypto;
    // used to verify that user is allowed to get access to the channel data
    PrivateKey signatureKey;
    protected PublicKey signatureKeyPublic;
    CryptoSettings channelSettings;

    protected Channel() {

    }

    public void load(UserIdentity identity, PublicKey signatureKey, byte[] pack)
            throws IOException, CryptoException {
        channelSettings = CryptoSettings.empty();
        signatureKeyPublic = signatureKey;

        ChannelBranchReader channelBranchReader =  new ChannelBranchReader();
        SecureAsymEnvelopeReader secureEnvelopeReader = new SecureAsymEnvelopeReader(identity.getMyself(),
                channelSettings, channelBranchReader);
        SignatureEnvelopeReader signatureReader = new SignatureEnvelopeReader(identity.getContactFinder(),
                channelSettings, secureEnvelopeReader);

        signatureReader.unpack(pack);

        parcelCrypto = secureEnvelopeReader.getParcelCrypto();
    }

    public byte[] sign(String token, String algorithm) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        return crypto.sign(token.getBytes(), signatureKey, CryptoSettings.signatureSettings(algorithm));
    }

    protected void create(CryptoSettings settings) throws CryptoException {
        this.channelSettings = settings;
        parcelCrypto = new ParcelCrypto(settings);

        ICryptoInterface crypto = Crypto.get();
        KeyPair keyPair = crypto.generateKeyPair(settings.asymmetricKeySizeChannelSign);
        signatureKey = keyPair.getPrivate();
        signatureKeyPublic = keyPair.getPublic();

        // The branch name is the 256 hash of the signature key. This makes sure that an attacker can't publish a fake
        // branch with a wrong signature key, e.g., to block an existing branch with the same name.
        byte hashResult[] = CryptoHelper.sha256Hash(signatureKeyPublic.getEncoded());
        branchName = CryptoHelper.toHex(hashResult);
    }

    public String getBranchName() {
        return branchName;
    }

    public ParcelCrypto getParcelCrypto() {
        return parcelCrypto;
    }

    protected byte[] pack(ContactPrivate sender, KeyId senderKey, Contact receiver, KeyId receiverKey)
            throws CryptoException, IOException {

        SignatureEnvelopeWriter signatureEnvelopeWriter = new SignatureEnvelopeWriter(sender, senderKey,
                parcelCrypto.getCryptoSettings(), null);

        SecureAsymEnvelopeWriter asymEnvelopeWriter = new SecureAsymEnvelopeWriter(receiver, receiverKey, parcelCrypto,
                channelSettings, signatureEnvelopeWriter);

        ChannelBranchWriter channelBranchWriter = new ChannelBranchWriter(asymEnvelopeWriter);
        return channelBranchWriter.pack(null);
    }

    public CryptoSettings getCryptoSettings() {
        return channelSettings;
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
