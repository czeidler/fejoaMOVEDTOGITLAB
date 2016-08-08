/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;

import java.io.*;
import java.util.Iterator;


class ChunkPointer implements IChunkPointer {
    // length of the "real" data, this is needed to find data for random access
    // Goal: don't rewrite previous blocks, support middle extents and make random access possible.
    // Using the data length makes this possible.
    private int dataLength;
    private BoxPointer boxPointer;

    private IChunk cachedChunk = null;
    protected int level;

    protected ChunkPointer(int level) {
        this.boxPointer = new BoxPointer();
        this.level = level;
    }

    protected ChunkPointer(BoxPointer hash, int dataLength, IChunk blob, int level) {
        if (hash != null)
            this.boxPointer = hash;
        else
            this.boxPointer = new BoxPointer();
        this.dataLength = dataLength;
        cachedChunk = blob;
        this.level = level;
    }

    @Override
    public int getPointerLength() {
        return BoxPointer.getPointerLength() + 4;
    }

    @Override
    public int getDataLength() throws IOException {
        if (cachedChunk != null)
            dataLength = cachedChunk.getDataLength();
        return dataLength;
    }

    public void setBoxPointer(BoxPointer boxPointer) {
        this.boxPointer = boxPointer;
    }

    public BoxPointer getBoxPointer() {
        return boxPointer;
    }

    @Override
    public IChunk getCachedChunk() {
        return cachedChunk;
    }

    @Override
    public void setCachedChunk(IChunk chunk) {
        this.cachedChunk = chunk;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public void setLevel(int level) {
        this.level = level;
    }

    public void read(DataInputStream inputStream) throws IOException {
        int value = inputStream.readInt();
        dataLength = value >> 1;
        boxPointer.read(inputStream);
    }

    public void write(DataOutputStream outputStream) throws IOException {
        int value = getDataLength() << 1;
        outputStream.writeInt(value);
        boxPointer.write(outputStream);
    }

    @Override
    public String toString() {
        String string = "l:" + dataLength;
        if (boxPointer != null)
            string+= "," + boxPointer.toString();
        return string;
    }
}


public class ChunkContainer extends ChunkContainerNode {
    /**
     * Create a new chunk container.
     *
     * @param blobAccessor
     */
    public ChunkContainer(IChunkAccessor blobAccessor, ChunkSplitter nodeSplitter) {
        super(blobAccessor, null, nodeSplitter, LEAF_LEVEL);
    }

    static public ChunkContainer read(IChunkAccessor blobAccessor, BoxPointer boxPointer)
            throws IOException, CryptoException {
        return new ChunkContainer(blobAccessor, boxPointer);
    }

    /**
     * Load an existing chunk container.
     *
     * @param blobAccessor
     * @param boxPointer
     * @throws IOException
     * @throws CryptoException
     */
    private ChunkContainer(IChunkAccessor blobAccessor, BoxPointer boxPointer)
            throws IOException, CryptoException {
        this(blobAccessor, blobAccessor.getChunk(boxPointer));
        that.setBoxPointer(boxPointer);
    }

    private ChunkContainer(IChunkAccessor blobAccessor, DataInputStream inputStream)
            throws IOException {
        super(blobAccessor, null, null, LEAF_LEVEL);
        read(inputStream);
    }

    @Override
    public int getBlobLength() {
        // number of slots;
        int length = getHeaderLength();
        length += super.getBlobLength();
        return length;
    }

    public int getNLevels() {
        return that.getLevel();
    }

    public class DataChunkPointer {
        final private IChunkPointer pointer;
        private DataChunk cachedChunk;
        final public long position;
        final public int chunkDataLength;

        private DataChunkPointer(IChunkPointer pointer, long position) throws IOException {
            this.pointer = pointer;
            this.position = position;
            this.chunkDataLength = pointer.getDataLength();
        }

        public DataChunk getDataChunk() throws IOException, CryptoException {
            if (cachedChunk == null)
                cachedChunk = ChunkContainer.this.getDataChunk(pointer);
            return cachedChunk;
        }

        public int getDataLength() {
            return chunkDataLength;
        }
    }

    public Iterator<DataChunkPointer> getChunkIterator(final long startPosition) {
        return new Iterator<DataChunkPointer>() {
            private long position = startPosition;

            @Override
            public boolean hasNext() {
                try {
                    if (position >= getDataLength())
                        return false;
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            public DataChunkPointer next() {
                try {
                    DataChunkPointer dataChunkPointer = get(position);
                    position = dataChunkPointer.position + dataChunkPointer.getDataLength();
                    return dataChunkPointer;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public void remove() {

            }
        };
    }

    public DataChunkPointer get(long position) throws IOException, CryptoException {
        SearchResult searchResult = findLevel0Node(position);
        if (searchResult.pointer == null)
            throw new IOException("Invalid position");
        return new DataChunkPointer(searchResult.pointer, searchResult.pointerDataPosition);
    }

    private SearchResult findLevel0Node(long position) throws IOException, CryptoException {
        long currentPosition = 0;
        IChunkPointer pointer = null;
        ChunkContainerNode containerNode = this;
        for (int i = 0; i < that.getLevel(); i++) {
            SearchResult result = findInNode(containerNode, position - currentPosition);
            if (result == null) {
                // find right most node blob
                return new SearchResult(getDataLength(), null, findRightMostNode());
            }
            currentPosition += result.pointerDataPosition;
            pointer = result.pointer;
            if (i == that.getLevel() - 1)
                break;
            else
                containerNode = containerNode.getNode(result.pointer);

        }

        return new SearchResult(currentPosition, pointer, containerNode);
    }

    private ChunkContainerNode findRightMostNode() throws IOException, CryptoException {
        ChunkContainerNode current = this;
        for (int i = 0; i < that.getLevel() - 1; i++) {
            IChunkPointer pointer = current.get(current.size() - 1);
            current = current.getNode(pointer);
        }
        return current;
    }

    private IChunkPointer putDataChunk(DataChunk blob) throws IOException, CryptoException {
        byte[] rawBlob = blob.getData();
        HashValue hash = blob.hash();
        HashValue boxedHash = blobAccessor.putChunk(rawBlob).key;
        BoxPointer boxPointer = new BoxPointer(hash, boxedHash);
        return new ChunkPointer(boxPointer, rawBlob.length, blob, DATA_LEVEL);
    }

    static class InsertSearchResult {
        final ChunkContainerNode containerNode;
        final int index;

        InsertSearchResult(ChunkContainerNode containerNode, int index) {
            this.containerNode = containerNode;
            this.index = index;
        }
    }

    private InsertSearchResult findInsertPosition(final long position) throws IOException, CryptoException {
        long currentPosition = 0;
        ChunkContainerNode node = this;
        int index = 0;
        for (int i = 0; i < that.getLevel(); i++) {
            long nodePosition = 0;
            long inNodeInsertPosition = position - currentPosition;
            index = 0;
            IChunkPointer pointer = null;
            for (; index < node.size(); index++) {
                pointer = node.get(index);
                long dataLength = pointer.getDataLength();
                if (nodePosition + dataLength > inNodeInsertPosition)
                    break;
                nodePosition += dataLength;
            }
            currentPosition += nodePosition;
            if (nodePosition > inNodeInsertPosition
                    || (i == that.getLevel() - 1 && nodePosition != inNodeInsertPosition)) {
                throw new IOException("Invalid insert position");
            }

            if (i < that.getLevel() - 1 && pointer != null)
                node = node.getNode(pointer);
        }

        return new InsertSearchResult(node, index);
    }

    public void insert(final DataChunk blob, final long position) throws IOException, CryptoException {
        InsertSearchResult searchResult = findInsertPosition(position);
        ChunkContainerNode containerNode = searchResult.containerNode;
        IChunkPointer blobChunkPointer = putDataChunk(blob);
        containerNode.addBlobPointer(searchResult.index, blobChunkPointer);
    }

    public void append(final DataChunk blob) throws IOException, CryptoException {
        insert(blob, getDataLength());
    }

    public void remove(long position, DataChunk dataChunk) throws IOException, CryptoException {
        remove(position, dataChunk.getDataLength());
    }

    public void remove(long position, long length) throws IOException, CryptoException {
        SearchResult searchResult = findLevel0Node(position);
        if (searchResult.pointer == null)
            throw new IOException("Invalid position");
        if (searchResult.pointer.getDataLength() != length)
            throw new IOException("Data length mismatch");

        ChunkContainerNode containerNode = searchResult.node;
        int indexInParent = containerNode.indexOf(searchResult.pointer);
        containerNode.removeBlobPointer(indexInParent, true);
    }

    @Override
    protected int getHeaderLength() {
        // 1 byte for number of levels
        int length = 1;
        return length;
    }

    @Override
    public void read(DataInputStream inputStream) throws IOException {
        readHeader(inputStream);
        super.read(inputStream);
    }

    @Override
    public void write(DataOutputStream outputStream) throws IOException {
        writeHeader(outputStream);
        super.write(outputStream);
    }

    public String printAll() throws Exception {
        String string = "Header: levels=" + that.getLevel() + ", length=" + getDataLength() + "\n";
        string += super.printAll();
        return string;
    }

    static final public byte FIXED_BLOCK_SPLITTER = 0;
    static final public byte RABIN_SPLITTER_DETAILED = 1;

    private void readHeader(DataInputStream inputStream) throws IOException {
        that.setLevel(inputStream.readByte());

        byte nodeSplitterType = inputStream.readByte();
        switch (nodeSplitterType) {
            case FIXED_BLOCK_SPLITTER:
                int blockSize = inputStream.readInt();
                setNodeSplitter(new FixedBlockSplitter(blockSize));
                break;
            case RABIN_SPLITTER_DETAILED:
                int targetSize = inputStream.readInt();
                int minSize = inputStream.readInt();
                int maxSize = inputStream.readInt();
                setNodeSplitter(new RabinSplitter(targetSize, minSize, maxSize));
                break;
            default:
                throw new IOException("Unknown node splitter type.");
        }
    }

    @Override
    protected void writeHeader(DataOutputStream outputStream) throws IOException {
        outputStream.writeByte(that.getLevel());
        if (nodeSplitter instanceof RabinSplitter) {
            RabinSplitter rabinSplitter = (RabinSplitter) nodeSplitter;
            outputStream.writeByte(RABIN_SPLITTER_DETAILED);
            outputStream.writeInt(rabinSplitter.getTargetChunkSize());
            outputStream.writeInt(rabinSplitter.getMinChunkSize());
            outputStream.writeInt(rabinSplitter.getMaxChunkSize());
        } else if (nodeSplitter instanceof  FixedBlockSplitter) {
            FixedBlockSplitter fixedBlockSplitter = (FixedBlockSplitter) nodeSplitter;
            outputStream.writeByte(FIXED_BLOCK_SPLITTER);
            outputStream.writeInt(fixedBlockSplitter.getBlockSize());
        } else {
            throw new IOException("Unsupported node splitter.");
        }

        super.writeHeader(outputStream);
    }
}
