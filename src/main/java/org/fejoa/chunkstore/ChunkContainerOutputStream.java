/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class ChunkContainerOutputStream extends OutputStream {
    private interface ITransaction {
        void write(int i) throws IOException;
        void finish() throws IOException;
    }

    class OverwriteTransaction implements ITransaction {
        // position of the first chunk that is overwritten
        private long writeStartPosition;
        private long bytesFlushed;
        private long bytesDeleted;
        private long bytesWritten;
        private boolean appending = false;
        private ChunkContainer.DataChunkPointer lastDeletedPointer;
        private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        public OverwriteTransaction(final long seekPosition, final long containerSize) throws IOException,
                CryptoException {
            goToStart(seekPosition, containerSize);
        }

        public void goToStart(final long seekPosition, final long containerSize) throws IOException, CryptoException {
            if (containerSize == 0)
                return;

            long start = seekPosition;
            // recalculate the last chunk if we append data because the last chunk may not be full
            if (seekPosition == containerSize)
                start--;

            lastDeletedPointer = container.get(start);
            removeChunk(lastDeletedPointer.position, lastDeletedPointer.chunkDataLength);
            writeStartPosition = lastDeletedPointer.position;

            DataChunk chunk = lastDeletedPointer.getDataChunk();
            for (int i = 0; i < seekPosition - writeStartPosition; i++)
                write(chunk.data[i]);
        }

        private void removeChunk(long position, long size) throws IOException, CryptoException {
            container.remove(position, size);
            bytesDeleted += size;
        }

        // write remaining data till we reached the end or a known chunk position
        private void finalizeWrite() throws IOException, CryptoException {
            while (lastDeletedPointer != null) {
                byte[] data = lastDeletedPointer.getDataChunk().getData();
                long bytesToWrite = bytesDeleted - bytesWritten;
                long start = data.length - bytesToWrite;
                for (int i = (int)start; i < data.length; i++)
                    write(data[i]);
            }
            flushChunk();
        }

        private void overwriteNextChunk() throws IOException, CryptoException {
            if (appending)
                return;
            long nextPosition = writeStartPosition + bytesFlushed;
            if (nextPosition == container.getDataLength()) {
                lastDeletedPointer = null;
                appending = true;
                return;
            }
            lastDeletedPointer = container.get(nextPosition);
            removeChunk(lastDeletedPointer.position, lastDeletedPointer.chunkDataLength);
        }

        @Override
        public void write(int i) throws IOException {
            if (lastDeletedPointer == null) {
                try {
                    overwriteNextChunk();
                } catch (CryptoException e) {
                    throw new IOException(e);
                }
            }
            outputStream.write(i);
            bytesWritten++;
            if (chunkSplitter.update((byte)i)) {
                chunkSplitter.reset();
                flushChunk();
            }
        }

        private void flushChunk() throws IOException {
            byte[] data = outputStream.toByteArray();
            if (data.length == 0)
                return;
            try {
                container.insert(new DataChunk(data), writeStartPosition + bytesFlushed);
                bytesFlushed += data.length;
                if (bytesFlushed == bytesDeleted) {
                    lastDeletedPointer = null;
                } else if (bytesFlushed > bytesDeleted) {
                    overwriteNextChunk();
                }
            } catch (CryptoException e) {
                throw new IOException(e);
            }
            outputStream = new ByteArrayOutputStream();
        }

        @Override
        public void finish() throws IOException {
            try {
                finalizeWrite();
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }
    }

    final private ChunkContainer container;
    final private ChunkSplitter chunkSplitter;
    private ITransaction currentTransaction;
    private long position = 0;

    public ChunkContainerOutputStream(ChunkContainer container) throws IOException {
        this(container, new RabinSplitter());
    }

    public ChunkContainerOutputStream(ChunkContainer container, ChunkSplitter chunkSplitter) throws IOException {
        this.container = container;
        this.chunkSplitter = chunkSplitter;
        seek(container.getDataLength());
    }

    public long length() throws IOException {
        return container.getDataLength();
    }

    public void seek(long position) throws IOException {
        if (currentTransaction != null)
            currentTransaction.finish();

        long length = length();
        if (position > length || position < 0)
            throw new IOException("Invalid seek position");
        try {
            currentTransaction = new OverwriteTransaction(position, length);
        } catch (CryptoException e) {
            throw new IOException(e);
        }
        this.position = position;
    }

    @Override
    public void write(int i) throws IOException {
        currentTransaction.write(i);
    }

    @Override
    public void flush() throws IOException {
        super.flush();

        if (currentTransaction != null)
            currentTransaction.finish();
    }

    @Override
    public void close() throws IOException {
        flush();

        currentTransaction = null;

        super.close();
    }
}
