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
import java.util.*;


public class DirectoryBox extends TypedBlob {
    public static class Entry {
        String name;
        boolean isFile;
        BoxPointer boxPointer;
        TypedBlob object;

        public Entry(String name, BoxPointer boxPointer, boolean isFile) {
            this.name = name;
            this.isFile = isFile;
            this.boxPointer = boxPointer;
        }

        protected Entry(boolean isFile) {
            this.isFile = isFile;
        }

        public void setObject(TypedBlob object) {
            this.object = object;
        }

        public TypedBlob getObject() {
            return object;
        }

        /**
         * Write the entry to a MessageDigest.
         *
         * @param messageDigest
         */
        public void hash(MessageDigest messageDigest) {
            messageDigest.update(name.getBytes());
            messageDigest.update(boxPointer.getDataHash().getBytes());
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
            this.boxPointer = data;
        }

        public void write(DataOutputStream outputStream) throws IOException {
            write(name, outputStream);
            boxPointer.write(outputStream);
        }

        public String getName() {
            return name;
        }

        public BoxPointer getBoxPointer() {
            return boxPointer;
        }

        public void setBoxPointer(BoxPointer boxPointer) {
            this.boxPointer = boxPointer;
        }
    }

    final private Map<String, Entry> entries = new HashMap<>();

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

    public Entry addDir(String name, BoxPointer pointer) {
        Entry entry = new Entry(name, pointer, false);
        if (put(name, entry))
            return entry;
        return null;
    }

    public Entry addFile(String name, BoxPointer pointer) {
        Entry entry = new Entry(name, pointer, true);
        if (put(name, entry))
            return entry;
        return null;
    }

    private boolean put(String name, Entry entry) {
        if (entries.containsKey(name))
            return false;
        entries.put(name, entry);
        return true;
    }

    public Collection<Entry> getEntries() {
        return entries.values();
    }

    public Entry getEntry(String name) {
        for (Entry entry : entries.values()) {
            if (entry.name.equals(name))
                return entry;
        }
        return null;
    }

    public Collection<Entry> getDirs() {
        List<Entry> children = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!entry.isFile)
                children.add(entry);
        }
        return children;
    }

    public Collection<Entry> getFiles() {
        List<Entry> children = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.isFile)
                children.add(entry);
        }
        return children;
    }

    @Override
    protected void readInternal(DataInputStream inputStream) throws IOException {
        long nDirs = inputStream.readLong();
        long nFiles = inputStream.readLong();
        for (long i = 0; i < nDirs; i++) {
            Entry entry = new Entry(false);
            entry.read(inputStream);
            entries.put(entry.getName(), entry);
        }
        for (long i = 0; i < nFiles; i++) {
            Entry entry = new Entry(true);
            entry.read(inputStream);
            entries.put(entry.getName(), entry);
        }
    }

    @Override
    protected void writeInternal(DataOutputStream outputStream) throws IOException {
        Collection<Entry> dirs = getDirs();
        Collection<Entry> files = getFiles();
        outputStream.writeLong(dirs.size());
        outputStream.writeLong(files.size());
        for (Entry entry : dirs)
            entry.write(outputStream);
        for (Entry entry : files)
            entry.write(outputStream);
    }

    public HashValue hash() {
        try {
            MessageDigest messageDigest = CryptoHelper.sha256Hash();
            messageDigest.reset();
            for (Entry entry : entries.values())
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
        for (Entry entry : entries.values())
            string += "\n" + entry.name + " (dir " + !entry.isFile + ")" + entry.boxPointer;
        return string;
    }
}
