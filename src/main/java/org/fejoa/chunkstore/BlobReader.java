/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.*;


public class BlobReader {
    final public static short COMMIT = 1;
    final public static short DIRECTORY = 2;
    final public static short FILE = 3;

    protected short type;
    protected DataInputStream inputStream;

    public BlobReader(byte[] content) throws IOException {
        this(new ByteArrayInputStream(content));
    }

    public BlobReader(InputStream inputStream) throws IOException {
        this.inputStream = new DataInputStream(inputStream);
        this.type = this.inputStream.readShort();
    }

    public short getType() {
        return type;
    }

    public TypedBlob read(IChunkAccessor blobAccessor) throws IOException {
        if (type == COMMIT)
            return readCommit();
        else if (type == DIRECTORY)
            return readDirectory();
        else if (type == FILE)
            return readFile(blobAccessor);
        return null;
    }

    public CommitBox readCommit() throws IOException {
        if (type != COMMIT)
            throw new IOException("Data is of type: " + type);
        return CommitBox.read(type, inputStream);
    }

    public DirectoryBox readDirectory() throws IOException {
        if (type != DIRECTORY)
            throw new IOException("Data is of type: " + type);
        return DirectoryBox.read(type, inputStream);
    }

    public FileBox readFile(IChunkAccessor blobAccessor) throws IOException {
        if (type != FILE)
            throw new IOException("Data is of type: " + type);
        return FileBox.read(type, inputStream, blobAccessor);
    }
}




