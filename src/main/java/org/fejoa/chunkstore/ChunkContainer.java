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
import java.util.List;


public class ChunkContainer extends ChunkContainerNode {
    public ChunkContainer(IBlobAccessor blobAccessor, HashValue hash) throws IOException {
        super(blobAccessor, null, BASE_LEVEL + 1);
        read(blobAccessor.getBlob(hash));
    }

    public ChunkContainer(IBlobAccessor blobAccessor) {
        super(blobAccessor, null, BASE_LEVEL + 1);
    }

    @Override
    public int getBlobLength() {
        // number of slots;
        int length = getHeaderLength();
        length += super.getBlobLength();
        return length;
    }

    public int getNLevels() {
        return that.level;
    }

    public static class DataChunkPosition {
        final public DataChunk chunk;
        final public long position;

        private DataChunkPosition(DataChunk chunk, long position) {
            this.chunk = chunk;
            this.position = position;
        }
    }

    public DataChunkPosition get(long position) throws IOException {
        SearchResult searchResult = findLevel0Node(position);
        if (searchResult.pointer == null)
            throw new IOException("Invalid position");
        return new DataChunkPosition((DataChunk)searchResult.pointer.getBlob(blobAccessor),
                searchResult.pointerDataPosition);
    }

    private SearchResult findLevel0Node(long position) throws IOException {
        long currentPosition = 0;
        BlobPointer pointer = null;
        BlobPointer containerPointer = that;
        for (int i = 0; i < that.level; i++) {
            SearchResult result = org.fejoa.chunkstore.ChunkContainerNode.findInNode(containerPointer, blobAccessor,
                    position - currentPosition);
            if (result == null) {
                // find right most node blob
                return new SearchResult(getDataLength(), null, findRightMostNodeBlob());
            }
            currentPosition += result.pointerDataPosition;
            pointer = result.pointer;
            if (i == that.level - 1)
                break;
            else
                containerPointer = result.pointer;
        }

        return new SearchResult(currentPosition, pointer, containerPointer);
    }

    private BlobPointer findRightMostNodeBlob() throws IOException {
        BlobPointer pointer = that;
        ChunkContainerNode current = this;
        for (int i = 0; i < that.level - 1; i++) {
            pointer = current.get(current.size() - 1);
            current = (ChunkContainerNode)pointer.getBlob(blobAccessor);
        }
        return pointer;
    }

    public void append(DataChunk blob) throws IOException {
        byte[] rawBlob = blob.getData();
        HashValue hash = blob.hash();
        blobAccessor.putBlock(hash, rawBlob);

        SearchResult insertPosition = findLevel0Node(getDataLength());
        BlobPointer pointer = new BlobPointer(hash, rawBlob.length, blob, BASE_LEVEL);
        ChunkContainerNode node = (ChunkContainerNode)insertPosition.nodePointer.getBlob(blobAccessor);
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
                int oldIndex = parent.indexOf(current.that);
                parent.removeBlobPointer(oldIndex);
                parent.addBlobPointer(oldIndex, current.that);
            }
            current = parent;
        } while (parent != null);
    }

    private void increaseLevel() {
        that.level++;
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
        String string = "Header: levels=" + that.level + ", length=" + getDataLength() + "\n";
        string += super.printAll();
        return string;
    }

    private void readHeader(DataInputStream inputStream) throws IOException {
        that.level = inputStream.readByte();
    }

    private void writeHeader(DataOutputStream outputStream) throws IOException {
        outputStream.writeByte(that.level);
    }

    public void flush() throws IOException {
        flush(that.level);
    }
}

class ChunkContainerNode implements IChunk {
    class BlobPointer {
        // length of the "real" data, this is needed to find data for random access
        // Goal: don't rewrite previous blocks, support middle extents and make random access possible.
        // Using the data length makes this possible.
        private int dataLength;
        private boolean packed;
        private HashValue blobHash;
        private IChunk cachedBlob = null;
        protected int level;

        private BlobPointer(int hashSize, int level) {
            this.blobHash = new HashValue(hashSize);
            this.level = level;
        }

        protected BlobPointer(HashValue hash, int dataLength, IChunk blob, int level) {
            if (hash != null)
                this.blobHash = new HashValue(hash);
            this.dataLength = dataLength;
            cachedBlob = blob;
            this.level = level;
        }

        public int getPointerLength() {
            return HashValue.HASH_SIZE + 4;
        }

        public int getDataLength() throws IOException {
            validate();
            return dataLength;
        }

        public HashValue getBlobHash() throws IOException {
            validate();
            return blobHash;
        }

        public void invalidate() {
            this.dataLength = -1;
            this.blobHash = null;
            this.packed = false;
        }

        private void validate() throws IOException {
            if (dataLength >= 0)
                return;
            if (cachedBlob == null)
                cachedBlob = getBlob(blobAccessor);
            dataLength = cachedBlob.getDataLength();
            blobHash = cachedBlob.hash();
        }

        public IChunk getCachedBlob() {
            return cachedBlob;
        }

        public IChunk getBlob(IBlobAccessor accessor) throws IOException {
            if (cachedBlob != null)
                return cachedBlob;

            DataInputStream inputStream = accessor.getBlob(blobHash);
            IChunk node;
            if (level == BASE_LEVEL)
                node = new DataChunk();
            else
                node = new ChunkContainerNode(blobAccessor, ChunkContainerNode.this, this);
            node.read(inputStream);
            cachedBlob = node;
            return cachedBlob;
        }

        public void read(DataInputStream inputStream) throws IOException {
            int value = inputStream.readInt();
            dataLength = value >> 1;
            packed = (value & 0x01) != 0;
            inputStream.readFully(blobHash.getBytes());
        }

        public void write(DataOutputStream outputStream) throws IOException {
            int value = dataLength << 1;
            if (packed)
                value |= 0X01;
            outputStream.writeInt(value);
            outputStream.write(blobHash.getBytes());
        }

        @Override
        public String toString() {
            String string = "l:" + dataLength + ",p:" + packed;
            if (blobHash != null)
                string+= "," + blobHash.toString();
            return string;
        }
    }

    static final protected int BASE_LEVEL = 0;

    final protected BlobPointer that;
    protected boolean onDisk = false;
    final protected ChunkContainerNode parent;
    final protected IBlobAccessor blobAccessor;
    private byte[] data;
    final private List<BlobPointer> slots = new ArrayList<>();
    protected int dataLength;
    private int maxNodeLength = 1024;

    public ChunkContainerNode(IBlobAccessor blobAccessor, ChunkContainerNode parent, BlobPointer that) {
        this.blobAccessor = blobAccessor;
        this.parent = parent;
        if (parent != null)
            this.maxNodeLength = parent.maxNodeLength;
        this.that = that;
    }

    public ChunkContainerNode(IBlobAccessor blobAccessor, ChunkContainerNode parent, int level) {
        this.blobAccessor = blobAccessor;
        this.parent = parent;
        if (parent != null)
            this.maxNodeLength = parent.maxNodeLength;
        this.that = new BlobPointer(null, -1, this, level);
    }

    public ChunkContainerNode getParent() {
        return parent;
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
    public int getDataLength() {
        return dataLength;
    }

    public int getBlobLength() {
        int length = 4; // number of slots;
        length += slots.size() * that.getPointerLength();

        return length;
    }

    static class SearchResult {
        final long pointerDataPosition;
        final BlobPointer pointer;
        final BlobPointer nodePointer;

        SearchResult(long pointerDataPosition, BlobPointer pointer, BlobPointer nodePointer) {
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
    static protected SearchResult findInNode(BlobPointer nodePointer, IBlobAccessor accessor, long dataPosition)
            throws IOException {
        ChunkContainerNode node = (ChunkContainerNode)nodePointer.getBlob(accessor);
        if (dataPosition > node.getDataLength())
            return null;

        long position = 0;
        for (int i = 0; i < node.slots.size(); i++) {
            BlobPointer pointer = node.slots.get(i);
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

        ChunkContainerNode left = new ChunkContainerNode(blobAccessor, this, that.level);
        ChunkContainerNode right = new ChunkContainerNode(blobAccessor, this, that.level);
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
        dataLength = 0;
        int nSlots = inputStream.readInt();
        for (int i = 0; i < nSlots; i++) {
            BlobPointer pointer = new BlobPointer(HashValue.HASH_SIZE, that.level - 1);
            pointer.read(inputStream);
            addBlobPointer(pointer);
        }
        onDisk = true;
    }

    @Override
    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.writeInt(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            BlobPointer pointer = slots.get(i);
            pointer.write(outputStream);
        }
    }

    public void flush(int level) throws IOException {
        byte[] data = getData();
        blobAccessor.putBlock(hash(data), data);
        if (level == BASE_LEVEL + 1)
            return;
        for (BlobPointer pointer : slots) {
            ChunkContainerNode blob = (ChunkContainerNode)pointer.getCachedBlob();
            if (blob == null || blob.onDisk)
                continue;
            blob.flush(level - 1);
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
        for (BlobPointer pointer : slots)
            string += pointer.toString() + "\n";
        return string;
    }

    protected String printAll() throws IOException {
        String string = toString();

        if (that.level == BASE_LEVEL + 1)
            return string;
        for (BlobPointer pointer : slots)
            string += ((ChunkContainerNode)pointer.getBlob(blobAccessor)).printAll();
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
        that.invalidate();
        data = null;
        onDisk = false;
    }

    protected void addBlobPointer(int index, BlobPointer pointer) throws IOException {
        slots.add(index, pointer);
        dataLength += pointer.getDataLength();
        invalidate();
    }

    protected void addBlobPointer(BlobPointer pointer) throws IOException {
        addBlobPointer(slots.size(), pointer);
    }

    protected void removeBlobPointer(int i) throws IOException {
        BlobPointer pointer = slots.remove(i);
        dataLength -= pointer.getDataLength();
        invalidate();
    }

    protected int size() {
        return slots.size();
    }

    protected BlobPointer get(int index) {
        return slots.get(index);
    }

    protected int indexOf(BlobPointer pointer) {
        return slots.indexOf(pointer);
    }

    protected void clear() {
        slots.clear();
        dataLength = 0;
        invalidate();
    }
}
