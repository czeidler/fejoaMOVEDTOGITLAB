/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.StreamHelper;

import java.io.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


abstract class DirectoryEntry {
    private String name;
    private BoxPointer dataPointer;
    private BoxPointer attrsDir = new BoxPointer();
    private Object object;

    public DirectoryEntry(String name, BoxPointer dataPointer) {
        this.name = name;
        this.dataPointer = dataPointer;
    }

    public DirectoryEntry() {

    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DirectoryEntry))
            return false;
        DirectoryEntry others = (DirectoryEntry)o;
        if (!name.equals(others.name))
            return false;
        if (!dataPointer.equals(others.dataPointer))
            return false;
        if (!attrsDir.equals(others.attrsDir))
            return false;
        return true;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public Object getObject() {
        return object;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BoxPointer getDataPointer() {
        return dataPointer;
    }

    public void setDataPointer(BoxPointer dataPointer) {
        this.dataPointer = dataPointer;
    }

    public BoxPointer getAttrsDir() {
        return attrsDir;
    }

    public void setAttrsDir(BoxPointer attrsDir) {
        this.attrsDir = attrsDir;
    }

    abstract void writeShortAttrs(DataOutputStream outputStream) throws IOException;
    abstract void readShortAttrs(DataInputStream inputStream) throws IOException;

    /**
     * Write the entry to a MessageDigest.
     *
     * @param messageDigest
     */
    public void hash(MessageDigest messageDigest) {
        DigestOutputStream digestOutputStream = new DigestOutputStream(new OutputStream() {
            @Override
            public void write(int i) throws IOException {

            }
        }, messageDigest);
        DataOutputStream outputStream = new DataOutputStream(digestOutputStream);
        try {
            write(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(DataOutputStream outputStream) throws IOException {
        StreamHelper.writeString(outputStream, name);
        writeShortAttrs(outputStream);
        dataPointer.write(outputStream);
        byte hasAttrsDir = 0x0;
        if (attrsDir != null)
            hasAttrsDir = 0x1;
        outputStream.writeByte(hasAttrsDir);
        if (attrsDir != null)
            attrsDir.write(outputStream);
    }

    public void read(DataInputStream inputStream) throws IOException {
        name = StreamHelper.readString(inputStream);
        readShortAttrs(inputStream);
        if (dataPointer == null)
            dataPointer = new BoxPointer();
        dataPointer.read(inputStream);
        byte hasAttrsDir = inputStream.readByte();
        if (hasAttrsDir == 0x1) {
            if (attrsDir == null)
                attrsDir = new BoxPointer();
            attrsDir.read(inputStream);
        }
    }
}

public class DirectoryBox extends TypedBlob {
    public static class Entry extends DirectoryEntry {
        boolean isFile;

        public Entry(String name, BoxPointer dataPointer, boolean isFile) {
            super(name, dataPointer);
            this.isFile = isFile;
        }

        protected Entry(boolean isFile) {
            this.isFile = isFile;
        }

        public boolean isFile() {
            return isFile;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry))
                return false;
            if (isFile != ((Entry) o).isFile)
                return false;
            return super.equals(o);
        }

        @Override
        void writeShortAttrs(DataOutputStream outputStream) throws IOException {

        }

        @Override
        void readShortAttrs(DataInputStream inputStream) throws IOException {

        }
    }

    final private Map<String, Entry> entries = new HashMap<>();

    private DirectoryBox() {
        super(BlobTypes.DIRECTORY);
    }

    static public DirectoryBox create() {
        return new DirectoryBox();
    }

    static public DirectoryBox read(IChunkAccessor accessor, BoxPointer boxPointer)
            throws IOException, CryptoException {
        ChunkContainer chunkContainer = ChunkContainer.read(accessor, boxPointer);
        return read(chunkContainer);
    }

    static public DirectoryBox read(ChunkContainer chunkContainer)
            throws IOException, CryptoException {
        return read(BlobTypes.DIRECTORY, new DataInputStream(new ChunkContainerInputStream(chunkContainer)));
    }

    static private DirectoryBox read(short type, DataInputStream inputStream) throws IOException {
        assert type == BlobTypes.DIRECTORY;
        DirectoryBox directoryBox = new DirectoryBox();
        directoryBox.read(inputStream);
        return directoryBox;
    }

    public Entry addDir(String name, BoxPointer pointer) {
        Entry entry = new Entry(name, pointer, false);
        put(name, entry);
        return entry;
    }

    public Entry addFile(String name, BoxPointer pointer) {
        Entry entry = new Entry(name, pointer, true);
        put(name, entry);
        return entry;
    }

    public void put(String name, Entry entry) {
        entries.put(name, entry);
    }

    public Entry remove(String entryName) {
        return entries.remove(entryName);
    }

    public Collection<Entry> getEntries() {
        return entries.values();
    }

    public Entry getEntry(String name) {
        for (Entry entry : entries.values()) {
            if (entry.getName().equals(name))
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
            string += "\n" + entry.getName() + " (dir " + !entry.isFile + ")" + entry.getDataPointer();
        return string;
    }
}
