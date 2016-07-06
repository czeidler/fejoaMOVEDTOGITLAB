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
import java.util.Iterator;


public class ChunkContainerOutputStream extends OutputStream {
    private interface ITransaction {
        void write(int i) throws IOException;
        void finish() throws IOException;
    }

    class AppendTransaction implements ITransaction {
        private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        public AppendTransaction(final long seekPosition, final long containerSize) {
            rewriteLastChunkToStart(seekPosition);


        }

        public void rewriteLastChunkToStart(final long seekPosition) {
            long start = seekPosition;
            // start with the previous chunk
            if (seekPosition > 0)
                start--;
            Iterator<ChunkContainer.DataChunkPosition> iter = container.getChunkIterator(start);
            if (iter.hasNext()) {
                ChunkContainer.DataChunkPosition chunkPosition = iter.next();
                //if (chunkPosition.position + chunkPosition.chunkDataLength < seekPosition)
            }
        }

        @Override
        public void write(int i) throws IOException {
            outputStream.write(i);
        }

        private void flushChunk() throws IOException {
            byte[] data = outputStream.toByteArray();
            if (data.length == 0)
                return;
            try {
                container.append(new DataChunk(data));
            } catch (CryptoException e) {
                throw new IOException(e);
            }
            outputStream = new ByteArrayOutputStream();
        }

        @Override
        public void finish() throws IOException {
            flushChunk();
        }
    }

    class WriteOverTransaction implements ITransaction {


        @Override
        public void write(int i) throws IOException {

        }

        @Override
        public void finish() throws IOException {

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
        this.position = position;
        if (position == length)
            currentTransaction = new AppendTransaction(position, length);
        else
            currentTransaction = new WriteOverTransaction();
    }

    @Override
    public void write(int i) throws IOException {
        currentTransaction.write(i);
        if (chunkSplitter.update((byte)i))
            seek(position);
    }

    @Override
    public void flush() throws IOException {
        super.flush();

        // seek does all the magic
        seek(position);
    }

    @Override
    public void close() throws IOException {
        flush();

        super.close();
    }
}
