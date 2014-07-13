/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.PositionInputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class MessageChannelInfo {
    private String subject;
    private List<Participant> participants = new ArrayList<>();

    public class Participant {
        public String address;
        public String uid;
    };

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
        @Override
        public byte[] pack() throws IOException, CryptoException {
            ByteArrayOutputStream packageData = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(packageData);

            if (subject != "")
                stream.writeBytes(SUBJECT + " " + subject + "\n");

            if (participants.size() > 0) {
                stream.writeBytes(PARTICIPANTS + " " + participants.size() + "\n");
                for (Participant participant : participants)
                    stream.writeBytes(participant.uid + " " + participant.address + "\n");
            }

            return packageData.toByteArray();
        }
    }

    class ParcelReader implements IParcelEnvelopeReader {
        @Override
        public byte[] unpack(byte[] parcel) throws Exception {
            PositionInputStream positionInputStream = new PositionInputStream(new ByteArrayInputStream(parcel));
            DataInputStream stream = new DataInputStream(positionInputStream);

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                switch (parts[0]) {
                    case SUBJECT:
                        if (parts.length != 2)
                            break;
                        subject = parts[1];
                        break;

                    case PARTICIPANTS:
                        if (parts.length != 2)
                            break;
                        int numberOfParticipants = Integer.parseInt(parts[1]);
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
                        break;
                }
            }
            return null;
        }
    }
};

