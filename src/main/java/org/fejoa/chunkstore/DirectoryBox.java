/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public class DirectoryBox extends TypedBlob {
    public static class Entry {
        String name;
        BoxPointer entryHash;

        public Entry(String name, BoxPointer entryHash) {
            this.name = name;
            this.entryHash = entryHash;
        }

        protected Entry() {

        }

        /**
         * Write the entry to a MessageDigest.
         *
         * @param messageDigest
         */
        public void hash(MessageDigest messageDigest) {
            messageDigest.update(name.getBytes());
            messageDigest.update(entryHash.getDataHash().getBytes());
        }

        private String readString(DataInputStream inputStream) throws IOException {
            int c = inputStream.read();
            StringBuilder builder = new StringBuilder("");
            while (c != -1 && c != 0) {
                builder.append((char) c);
                c = inputStream.read();
            }
            return builder.toString();
        }

        private void write(String string, DataOutputStream outputStream) throws IOException {
            outputStream.write(string.getBytes());
            outputStream.write(0);
        }

        public void read(DataInputStream inputStream) throws IOException {
            String name = readString(inputStream);

            BoxPointer data = new BoxPointer();
            data.read(inputStream);

            this.name = name;
            this.entryHash = data;
        }

        public void write(DataOutputStream outputStream) throws IOException {
            write(name, outputStream);
            entryHash.write(outputStream);
        }

        public String getName() {
            return name;
        }

        public BoxPointer getEntryHash() {
            return entryHash;
        }
    }

    final private List<Entry> entries = new ArrayList<>();

    private DirectoryBox() {
        super(BlobReader.DIRECTORY);
    }

    static public DirectoryBox create() {
        return new DirectoryBox();
    }

    static public DirectoryBox read(short type, DataInputStream inputStream) throws IOException {
        assert type == BlobReader.DIRECTORY;
        DirectoryBox directoryBox = new DirectoryBox();
        directoryBox.read(inputStream);
        return directoryBox;
    }

    public void addEntry(String name, BoxPointer pointer) {
        entries.add(new Entry(name, pointer));
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public Entry getEntry(String name) {
        for (Entry entry : entries) {
            if (entry.name.equals(name))
                return entry;
        }
        return null;
    }

    @Override
    protected void readInternal(DataInputStream inputStream) throws IOException {
        long size = inputStream.readLong();
        for (long i = 0; i < size; i++) {
            Entry entry = new Entry();
            entry.read(inputStream);
            entries.add(entry);
        }
    }

    @Override
    protected void writeInternal(DataOutputStream outputStream) throws IOException {
        outputStream.writeLong(entries.size());
        for (Entry entry : entries)
            entry.write(outputStream);
    }

    public HashValue hash() {
        try {
            MessageDigest messageDigest = CryptoHelper.sha256Hash();
            messageDigest.reset();
            for (Entry entry : entries)
                entry.hash(messageDigest);

            return new HashValue(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        String string = "Directory Entries:";
        for (Entry entry : entries)
            string += "\n" + entry.name + " " + entry.entryHash;
        return string;
    }
}
