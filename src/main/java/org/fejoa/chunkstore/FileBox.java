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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;


public class FileBox extends TypedBlob {
    private IChunkAccessor accessor;
    private ChunkContainer chunkContainer;

    private BoxPointer fileAttrs;
    private HashValue boxAttrs;

    private FileBox() {
        super(BlobReader.FILE);
    }

    static public FileBox create(IChunkAccessor accessor) {
        FileBox fileBox = new FileBox();
        fileBox.accessor = accessor;
        fileBox.chunkContainer = new ChunkContainer(accessor);
        return fileBox;
    }

    static public FileBox read(short type, DataInputStream inputStream, IChunkAccessor accessor) throws IOException {
        assert type == BlobReader.FILE;
        FileBox fileBox = new FileBox();
        fileBox.accessor = accessor;
        fileBox.read(inputStream);
        return fileBox;
    }

    public HashValue hash() throws IOException {
        try {
            MessageDigest messageDigest = CryptoHelper.sha256Hash();
            messageDigest.reset();
            Iterator<ChunkContainer.DataChunkPosition> iterator = chunkContainer.getChunkIterator(0);
            while (iterator.hasNext())
                messageDigest.update(iterator.next().chunk.getData());

            return new HashValue(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    @Override
    protected void readInternal(DataInputStream inputStream) throws IOException {
        // TODO: read/write header and file/box attrs
        chunkContainer = new ChunkContainer(accessor, inputStream);
    }

    @Override
    protected void writeInternal(DataOutputStream outputStream) throws IOException, CryptoException {
        chunkContainer.flush(true);
        // write header node
        chunkContainer.write(outputStream);
    }

    public ChunkContainer getChunkContainer() {
        return chunkContainer;
    }

    @Override
    public String toString() {
        if (chunkContainer == null)
            return "empty";
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(chunkContainer);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            StreamHelper.copy(inputStream, outputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return "invalid";
        }
        return "FileBox: " + new String(outputStream.toByteArray());
    }
}
