/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


interface IChunkPointer {
    int getPointerLength();
    int getDataLength() throws IOException;
    HashValue getChunkHash();
    void read(DataInputStream inputStream) throws IOException;
    void write(DataOutputStream outputStream) throws IOException;
    IChunk getCachedChunk();
    void setCachedChunk(IChunk chunk);
    int getLevel();
    void setLevel(int level);
}

class ChunkPointer implements IChunkPointer {
    // length of the "real" data, this is needed to find data for random access
    // Goal: don't rewrite previous blocks, support middle extents and make random access possible.
    // Using the data length makes this possible.
    private int dataLength;
    private boolean packed;
    private HashValue chunkHash;
    private IChunk cachedChunk = null;
    protected int level;

    protected ChunkPointer(int hashSize, int level) {
        this.chunkHash = new HashValue(hashSize);
        this.level = level;
    }

    protected ChunkPointer(HashValue hash, int dataLength, IChunk blob, int level) {
        if (hash != null)
            this.chunkHash = new HashValue(hash);
        this.dataLength = dataLength;
        cachedChunk = blob;
        this.level = level;
    }

    @Override
    public int getPointerLength() {
        return HashValue.HASH_SIZE + 4;
    }

    @Override
    public int getDataLength() throws IOException {
        if (cachedChunk != null)
            dataLength = cachedChunk.getDataLength();
        return dataLength;
    }

    public HashValue getChunkHash() {
        if (cachedChunk != null)
            chunkHash = cachedChunk.hash();
        return chunkHash;
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
        packed = (value & 0x01) != 0;
        inputStream.readFully(chunkHash.getBytes());
    }

    public void write(DataOutputStream outputStream) throws IOException {
        int value = dataLength << 1;
        if (packed)
            value |= 0X01;
        outputStream.writeInt(value);
        outputStream.write(getChunkHash().getBytes());
    }

    @Override
    public String toString() {
        String string = "l:" + dataLength + ",p:" + packed;
        if (chunkHash != null)
            string+= "," + chunkHash.toString();
        return string;
    }
}


public class ChunkContainer extends ChunkContainerNode {
    public ChunkContainer(IChunkAccessor blobAccessor, HashValue hash) throws IOException {
        this(blobAccessor, blobAccessor.getBlob(hash));
    }

    public ChunkContainer(IChunkAccessor blobAccessor, DataInputStream inputStream) throws IOException {
        super(blobAccessor, null, LEAF_LEVEL);
        read(inputStream);
    }

    public ChunkContainer(IChunkAccessor blobAccessor) {
        super(blobAccessor, null, LEAF_LEVEL);
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

    public static class DataChunkPosition {
        final public DataChunk chunk;
        final public long position;

        private DataChunkPosition(DataChunk chunk, long position) {
            this.chunk = chunk;
            this.position = position;
        }
    }

    public Iterator<DataChunkPosition> getChunkIterator(final long startPosition) {
        return new Iterator<DataChunkPosition>() {
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
            public DataChunkPosition next() {
                try {
                    DataChunkPosition dataChunkPosition = get(position);
                    position += dataChunkPosition.position + dataChunkPosition.chunk.getDataLength();
                    return dataChunkPosition;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public void remove() {

            }
        };
    }

    public DataChunkPosition get(long position) throws IOException {
        SearchResult searchResult = findLevel0Node(position);
        if (searchResult.pointer == null)
            throw new IOException("Invalid position");
        DataChunk dataChunk = (DataChunk)getBlob(searchResult.pointer);
        return new DataChunkPosition(dataChunk, searchResult.pointerDataPosition);
    }

    private SearchResult findLevel0Node(long position) throws IOException {
        long currentPosition = 0;
        IChunkPointer pointer = null;
        IChunkPointer containerPointer = that;
        for (int i = 0; i < that.getLevel(); i++) {
            SearchResult result = findInNode(containerPointer, position - currentPosition);
            if (result == null) {
                // find right most node blob
                return new SearchResult(getDataLength(), null, findRightMostNodeBlob());
            }
            currentPosition += result.pointerDataPosition;
            pointer = result.pointer;
            if (i == that.getLevel() - 1)
                break;
            else
                containerPointer = result.pointer;
        }

        return new SearchResult(currentPosition, pointer, containerPointer);
    }

    private IChunkPointer findRightMostNodeBlob() throws IOException {
        IChunkPointer pointer = that;
        ChunkContainerNode current = this;
        for (int i = 0; i < that.getLevel() - 1; i++) {
            pointer = current.get(current.size() - 1);
            current = (ChunkContainerNode)getBlob(pointer);
        }
        return pointer;
    }

    public void append(DataChunk blob) throws IOException {
        byte[] rawBlob = blob.getData();
        HashValue hash = blob.hash();
        blobAccessor.putChunk(hash, rawBlob);

        SearchResult insertPosition = findLevel0Node(getDataLength());
        IChunkPointer pointer = new ChunkPointer(hash, rawBlob.length, blob, DATA_LEVEL);
        ChunkContainerNode node = (ChunkContainerNode)getBlob(insertPosition.nodePointer);
        node.addBlobPointer(pointer);

        // split node
        ChunkContainerNode parent;
        ChunkContainerNode current = node;
        do {
            parent = current.getParent();
            if (current.getBlobLength() > current.getMaxNodeLength()) {
                SplitResult splitResult = current.split();
                if (parent != null) {
                    int oldIndex = parent.indexOf(current.that);
                    parent.removeBlobPointer(oldIndex);
                    parent.addBlobPointer(oldIndex, splitResult.left.that);
                    parent.addBlobPointer(oldIndex + 1, splitResult.right.that);
                } else {
                    this.clear();
                    this.addBlobPointer(splitResult.left.that);
                    this.addBlobPointer(splitResult.right.that);
                    increaseLevel();
                }
            } else if (parent != null) {
                // update parent
                parent.invalidate();
            }
            current = parent;
        } while (parent != null);
    }

    private void increaseLevel() {
        that.setLevel(that.getLevel() + 1);
        invalidate();
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

    public String printAll() throws IOException {
        String string = "Header: levels=" + that.getLevel() + ", length=" + getDataLength() + "\n";
        string += super.printAll();
        return string;
    }

    private void readHeader(DataInputStream inputStream) throws IOException {
        that.setLevel(inputStream.readByte());
    }

    private void writeHeader(DataOutputStream outputStream) throws IOException {
        outputStream.writeByte(that.getLevel());
    }

    public void flush(boolean childOnly) throws IOException {
        flush(that.getLevel(), childOnly);
    }
}

class ChunkContainerNode implements IChunk {
    static final protected int DATA_LEVEL = 0;
    static final protected int LEAF_LEVEL = DATA_LEVEL + 1;
    static final public int DEFAULT_MAX_NODE_LENGTH = 1024;

    final protected IChunkPointer that;
    protected boolean onDisk = false;
    protected ChunkContainerNode parent;
    final protected IChunkAccessor blobAccessor;
    private byte[] data;
    final private List<IChunkPointer> slots = new ArrayList<>();
    private int maxNodeLength = DEFAULT_MAX_NODE_LENGTH;
    // max node length that get assigned to new child nodes
    private int childMaxNodeLength = DEFAULT_MAX_NODE_LENGTH;

    public ChunkContainerNode(IChunkAccessor blobAccessor, ChunkContainerNode parent, IChunkPointer that) {
        this.blobAccessor = blobAccessor;
        this.parent = parent;
        this.that = that;
    }

    public ChunkContainerNode(IChunkAccessor blobAccessor, ChunkContainerNode parent, int level) {
        this.blobAccessor = blobAccessor;
        this.parent = parent;
        this.that = new ChunkPointer(null, -1, this, level);
    }

    public void setParent(ChunkContainerNode parent) {
        this.parent = parent;
    }

    public ChunkContainerNode getParent() {
        return parent;
    }

    protected boolean isDataPointer(IChunkPointer pointer) {
        return pointer.getLevel() == ChunkContainer.DATA_LEVEL;
    }

    public void setMaxNodeLength(int maxNodeLength) {
        this.maxNodeLength = maxNodeLength;
    }

    public int getMaxNodeLength() {
        return maxNodeLength;
    }

    protected int getHeaderLength() {
        return 0;
    }

    public int getMaxSlots() {
        return (getMaxNodeLength() - getHeaderLength() - 4) / that.getPointerLength();
    }

    @Override
    public int getDataLength() throws IOException {
        return calculateDataLength();
    }

    public IChunk getBlob(IChunkPointer pointer) throws IOException {
        IChunk cachedChunk = pointer.getCachedChunk();
        if (cachedChunk != null)
            return cachedChunk;

        DataInputStream inputStream = blobAccessor.getBlob(pointer.getChunkHash());
        if (isDataPointer(pointer))
            cachedChunk = new DataChunk();
        else {
            ChunkContainerNode node = new ChunkContainerNode(blobAccessor, this, pointer);
            node.setMaxNodeLength(childMaxNodeLength);
            cachedChunk = node;
        }
        cachedChunk.read(inputStream);
        pointer.setCachedChunk(cachedChunk);
        return cachedChunk;
    }

    protected int calculateDataLength() throws IOException {
        int length = 0;
        for (IChunkPointer pointer : slots)
            length += pointer.getDataLength();
        return length;
    }

    public int getBlobLength() {
        int length = 4; // number of slots;
        length += slots.size() * that.getPointerLength();

        return length;
    }

    static class SearchResult {
        final long pointerDataPosition;
        final IChunkPointer pointer;
        final IChunkPointer nodePointer;

        SearchResult(long pointerDataPosition, IChunkPointer pointer, IChunkPointer nodePointer) {
            this.pointerDataPosition = pointerDataPosition;
            this.pointer = pointer;
            this.nodePointer = nodePointer;
        }
    }

    /**
     *
     * @param dataPosition relative to this node
     * @return
     */
    protected SearchResult findInNode(IChunkPointer nodePointer, long dataPosition)
            throws IOException {
        ChunkContainerNode node = (ChunkContainerNode)getBlob(nodePointer);
        if (dataPosition > node.getDataLength())
            return null;

        long position = 0;
        for (int i = 0; i < node.slots.size(); i++) {
            IChunkPointer pointer = node.slots.get(i);
            long dataLength = pointer.getDataLength();
            if (position + dataLength > dataPosition)
                return new SearchResult(position, pointer, nodePointer);
            position += dataLength;
        }
        return null;
    }

    class SplitResult {
        ChunkContainerNode left;
        ChunkContainerNode right;
    }

    protected SplitResult split() throws IOException {
        int pointerIndex = getMaxSlots();
        assert pointerIndex < slots.size();

        ChunkContainerNode left = new ChunkContainerNode(blobAccessor, parent, that.getLevel());
        ChunkContainerNode right = new ChunkContainerNode(blobAccessor, parent, that.getLevel());
        left.setMaxNodeLength(childMaxNodeLength);
        right.setMaxNodeLength(childMaxNodeLength);
        left.setMaxNodeLength(getMaxNodeLength());
        right.setMaxNodeLength(getMaxNodeLength());
        for (int i = 0 ; i < pointerIndex; i++)
            left.addBlobPointer(slots.get(i));
        for (int i = pointerIndex; i < slots.size(); i++)
            right.addBlobPointer(slots.get(i));

        SplitResult splitResult = new SplitResult();
        splitResult.left = left;
        splitResult.right = right;
        return splitResult;
    }

    @Override
    public void read(DataInputStream inputStream) throws IOException {
        slots.clear();
        int nSlots = inputStream.readInt();
        for (int i = 0; i < nSlots; i++) {
            IChunkPointer pointer = new ChunkPointer(HashValue.HASH_SIZE, that.getLevel() - 1);
            pointer.read(inputStream);
            addBlobPointer(pointer);
        }
        onDisk = true;
    }

    @Override
    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.writeInt(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            IChunkPointer pointer = slots.get(i);
            pointer.write(outputStream);
        }
    }

    /**
     *
     * @param level
     * @param childOnly only child nodes are flushed
     * @throws IOException
     */
    public void flush(int level, boolean childOnly) throws IOException {
        if (!childOnly) {
            byte[] data = getData();
            blobAccessor.putChunk(hash(data), data);
        }
        if (level <= LEAF_LEVEL)
            return;
        for (IChunkPointer pointer : slots) {
            ChunkContainerNode blob = (ChunkContainerNode)pointer.getCachedChunk();
            if (blob == null || blob.onDisk)
                continue;
            blob.flush(level - 1, false);
        }

        onDisk = true;
    }

    @Override
    public byte[] getData() throws IOException {
        if (data != null)
            return data;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        write(new DataOutputStream(outputStream));
        data = outputStream.toByteArray();
        return data;
    }

    @Override
    public String toString() {
        String string = "Hash: " + hash() + "\n";
        for (IChunkPointer pointer : slots)
            string += pointer.toString() + "\n";
        return string;
    }

    protected String printAll() throws IOException {
        String string = toString();

        if (that.getLevel() == LEAF_LEVEL)
            return string;
        for (IChunkPointer pointer : slots)
            string += ((ChunkContainerNode)getBlob(pointer)).printAll();
        return string;
    }

    @Override
    public HashValue hash() {
        try {
            return hash(getData());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public HashValue hash(byte[] data) {
        return new HashValue(CryptoHelper.sha256Hash(data));
    }

    protected void invalidate() {
        data = null;
        onDisk = false;
    }

    protected void addBlobPointer(int index, IChunkPointer pointer) throws IOException {
        slots.add(index, pointer);
        if (!isDataPointer(pointer) && pointer.getCachedChunk() != null)
            ((ChunkContainerNode)pointer.getCachedChunk()).setParent(this);
        invalidate();
    }

    protected void addBlobPointer(IChunkPointer pointer) throws IOException {
        addBlobPointer(slots.size(), pointer);
    }

    protected void removeBlobPointer(int i) throws IOException {
        slots.remove(i);
        invalidate();
    }

    protected int size() {
        return slots.size();
    }

    protected IChunkPointer get(int index) {
        return slots.get(index);
    }

    protected int indexOf(IChunkPointer pointer) {
        return slots.indexOf(pointer);
    }

    protected void clear() {
        slots.clear();
        invalidate();
    }
}
