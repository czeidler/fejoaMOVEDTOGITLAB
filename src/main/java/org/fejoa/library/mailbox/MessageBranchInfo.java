/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.support.PositionInputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class MessageBranchInfo {
    private String uid = "";
    private String subject = "";
    private List<Participant> participants = new ArrayList<>();
    private boolean newlyCreated = true;

    public class Participant {
        public String address;
        public String uid;
    };

    public void load(ParcelCrypto parcelCrypto, IContactFinder contactFinder, byte[] pack) throws IOException,
            CryptoException {
        newlyCreated = false;

        ParcelReader branchInfoReader =  new ParcelReader();
        SecureSymEnvelopeReader secureSymEnvelopeReader = new SecureSymEnvelopeReader(parcelCrypto, branchInfoReader);

        CryptoSettings.SignatureSettings signatureSettings = CryptoSettings.empty().signature;
        SignatureEnvelopeReader signatureReader = new SignatureEnvelopeReader(contactFinder, signatureSettings,
                secureSymEnvelopeReader);

        signatureReader.unpack(pack);
    }

    public byte[] write(ParcelCrypto parcelCrypto, ContactPrivate sender, KeyId senderKey,
                        CryptoSettings.SignatureSettings signatureSettings)
            throws CryptoException, IOException {

        SignatureEnvelopeWriter signatureEnvelopeWriter
                = new SignatureEnvelopeWriter(sender, senderKey, signatureSettings, null);
        SecureSymEnvelopeWriter secureSymEnvelopeWriter = new SecureSymEnvelopeWriter(parcelCrypto,
                signatureEnvelopeWriter);

        ParcelWriter parcelWriter = new ParcelWriter(secureSymEnvelopeWriter);
        return parcelWriter.pack(null);
    }

    public String getUid() {
        return uid;
    }

    public boolean isNewlyCreated() {
        return newlyCreated;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }

    public void addParticipant(String address, String uid) {
        Participant participant = new Participant();
        participant.address = address;
        participant.uid = uid;
        participants.add(participant);
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    final String SUBJECT = "subject";
    final String PARTICIPANTS = "participants";

    class ParcelWriter implements IParcelEnvelopeWriter {
        private IParcelEnvelopeWriter childWriter;

        public ParcelWriter(IParcelEnvelopeWriter childWriter) {
            this.childWriter = childWriter;
        }

        @Override
        public byte[] pack(byte[] parcel) throws IOException, CryptoException {
            ByteArrayOutputStream packageData = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(packageData);

            if (!subject.equals(""))
                stream.writeBytes(SUBJECT + " " + subject + "\n");

            if (participants.size() > 0) {
                stream.writeBytes(PARTICIPANTS + " " + participants.size() + "\n");
                for (Participant participant : participants)
                    stream.writeBytes(participant.uid + " " + participant.address + "\n");
            }

            byte[] pack = packageData.toByteArray();
            if (childWriter != null)
                return childWriter.pack(pack);
            return pack;
        }
    }

    class ParcelReader implements IParcelEnvelopeReader {
        @Override
        public byte[] unpack(byte[] parcel) throws IOException {
            PositionInputStream positionInputStream = new PositionInputStream(new ByteArrayInputStream(parcel));
            DataInputStream stream = new DataInputStream(positionInputStream);

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf(SUBJECT) == 0)
                    subject = line.substring(SUBJECT.length() + 1);
                else if (line.indexOf(PARTICIPANTS) == 0) {
                    String data = line.substring(PARTICIPANTS.length() + 1);
                    int numberOfParticipants = Integer.parseInt(data);
                    for (int i = 0; i < numberOfParticipants; i++) {
                        line = reader.readLine();
                        if (line == null)
                            break;
                        String[] participantParts = line.split(" ");
                        if (participantParts.length != 2)
                            break;
                        Participant participant = new Participant();
                        participant.uid = participantParts[0];
                        participant.address = participantParts[1];
                        participants.add(participant);
                    }
                }
            }
            return null;
        }
    }
};

