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

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


interface IChunkPointer {
    int getPointerLength();
    int getDataLength() throws IOException;
    BoxPointer getBoxPointer();
    void setBoxPointer(BoxPointer boxPointer);
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
    private BoxPointer boxPointer;

    private boolean packed;
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
        packed = (value & 0x01) != 0;
        boxPointer.read(inputStream);
    }

    public void write(DataOutputStream outputStream) throws IOException {
        int value = getDataLength() << 1;
        if (packed)
            value |= 0X01;
        outputStream.writeInt(value);
        boxPointer.write(outputStream);
    }

    @Override
    public String toString() {
        String string = "l:" + dataLength + ",p:" + packed;
        if (boxPointer != null)
            string+= "," + boxPointer.toString();
        return string;
    }
}


public class ChunkContainer extends ChunkContainerNode {
    public ChunkContainer(IChunkAccessor blobAccessor, BoxPointer hash) throws IOException, CryptoException {
        this(blobAccessor, blobAccessor.getChunk(hash));
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
                cachedChunk = (DataChunk) getBlob(pointer);
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

    private IChunkPointer findRightMostNodeBlob() throws IOException, CryptoException {
        IChunkPointer pointer = that;
        ChunkContainerNode current = this;
        for (int i = 0; i < that.getLevel() - 1; i++) {
            pointer = current.get(current.size() - 1);
            current = (ChunkContainerNode)getBlob(pointer);
        }
        return pointer;
    }

    private IChunkPointer putDataChunk(DataChunk blob) throws IOException, CryptoException {
        byte[] rawBlob = blob.getData();
        HashValue hash = blob.hash();
        HashValue boxedHash = blobAccessor.putChunk(rawBlob).key;
        BoxPointer boxPointer = new BoxPointer(hash, boxedHash);
        return new ChunkPointer(boxPointer, rawBlob.length, blob, DATA_LEVEL);
    }

    static class InsertSearchResult {
        final IChunkPointer containerPointer;
        final int index;

        InsertSearchResult(IChunkPointer containerPointer, int index) {
            this.containerPointer = containerPointer;
            this.index = index;
        }
    }

    private InsertSearchResult findInsertPosition(final long position) throws IOException, CryptoException {
        long currentPosition = 0;
        IChunkPointer containerPointer = that;
        int index = 0;
        for (int i = 0; i < that.getLevel(); i++) {
            ChunkContainerNode node = (ChunkContainerNode)getBlob(containerPointer);
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
                containerPointer = pointer;
        }

        return new InsertSearchResult(containerPointer, index);
    }

    public void insert(final DataChunk blob, final long position) throws IOException, CryptoException {
        InsertSearchResult searchResult = findInsertPosition(position);
        ChunkContainerNode containerNode = (ChunkContainerNode) getBlob(searchResult.containerPointer);
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

        ChunkContainerNode containerNode = (ChunkContainerNode) getBlob(searchResult.nodePointer);
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

    private void readHeader(DataInputStream inputStream) throws IOException {
        that.setLevel(inputStream.readByte());
    }

    @Override
    protected void writeHeader(DataOutputStream outputStream) throws IOException {
        outputStream.writeByte(that.getLevel());
        super.writeHeader(outputStream);
    }
}

class ChunkContainerNode implements IChunk {
    static final protected int DATA_LEVEL = 0;
    static final protected int LEAF_LEVEL = DATA_LEVEL + 1;

    final protected IChunkPointer that;
    protected boolean onDisk = false;
    protected ChunkContainerNode parent;
    final protected IChunkAccessor blobAccessor;
    private byte[] data;
    private HashValue dataHash;
    final private List<IChunkPointer> slots = new ArrayList<>();
    private ChunkSplitter nodeSplitter;

    public ChunkContainerNode(IChunkAccessor blobAccessor, ChunkContainerNode parent, IChunkPointer that) {
        this.blobAccessor = blobAccessor;
        this.parent = parent;
        this.that = that;
        if (parent != null)
            setNodeSplitter(parent.nodeSplitter);
    }

    public ChunkContainerNode(IChunkAccessor blobAccessor, ChunkContainerNode parent, int level) {
        this.blobAccessor = blobAccessor;
        this.parent = parent;
        this.that = new ChunkPointer(null, -1, this, level);
        if (parent != null)
            setNodeSplitter(parent.nodeSplitter);
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

    public void setNodeSplitter(ChunkSplitter nodeSplitter) {
        this.nodeSplitter = nodeSplitter;
        nodeSplitter.reset();
    }

    protected int getHeaderLength() {
        return 0;
    }

    @Override
    public int getDataLength() throws IOException {
        return calculateDataLength();
    }

    public IChunk getBlob(IChunkPointer pointer) throws IOException, CryptoException {
        IChunk cachedChunk = pointer.getCachedChunk();
        if (cachedChunk != null)
            return cachedChunk;

        DataInputStream inputStream = blobAccessor.getChunk(pointer.getBoxPointer());
        if (isDataPointer(pointer))
            cachedChunk = new DataChunk();
        else {
            ChunkContainerNode node = new ChunkContainerNode(blobAccessor, this, pointer);
            node.setNodeSplitter(nodeSplitter);
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
        int length = 4; // number of slots (int);
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
    protected SearchResult findInNode(IChunkPointer nodePointer, final long dataPosition)
            throws IOException, CryptoException {
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
        return new SearchResult(position, null, nodePointer);
    }

    @Override
    public void read(DataInputStream inputStream) throws IOException {
        slots.clear();
        int nSlots = inputStream.readInt();
        for (int i = 0; i < nSlots; i++) {
            IChunkPointer pointer = new ChunkPointer(that.getLevel() - 1);
            pointer.read(inputStream);
            addBlobPointer(pointer);
        }
        onDisk = true;
    }

    protected void writeHeader(DataOutputStream outputStream) throws IOException {

    }
    @Override
    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.writeInt(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            IChunkPointer pointer = slots.get(i);
            pointer.write(outputStream);
        }
    }

    private ChunkContainerNode findRightNeighbour() throws IOException, CryptoException {
        if (parent == null)
            return null;

        // find common parent
        int indexInParent = -1;
        ChunkContainerNode parent = this;
        int levelDiff = 0;
        while (parent.getParent() != null) {
            levelDiff++;
            IChunkPointer pointerInParent = parent.that;
            parent = parent.getParent();
            indexInParent = parent.indexOf(pointerInParent);
            assert indexInParent >= 0;
            if (indexInParent != parent.size() - 1)
                break;
        }

        // is last pointer?
        if (indexInParent == parent.size() - 1)
            return null;

        IChunk blob = parent.getBlob(parent.get(indexInParent + 1));
        ChunkContainerNode neighbour = (ChunkContainerNode) blob;
        for (int i = 0; i < levelDiff - 1; i++)
            neighbour = (ChunkContainerNode) neighbour.getBlob(neighbour.get(0));

        assert neighbour.that.getLevel() == that.getLevel();
        return neighbour;
    }

    private void splitAt(int index) throws IOException {
        ChunkContainerNode right = new ChunkContainerNode(blobAccessor, parent, that.getLevel());
        right.setNodeSplitter(nodeSplitter);
        while (size() > index)
            right.addBlobPointer(removeBlobPointer(index));
        if (parent != null) {
            int inParentIndex = parent.indexOf(that);
            parent.addBlobPointer(inParentIndex + 1, right.that);
        } else {
            // move item item to new child
            ChunkContainerNode left = new ChunkContainerNode(blobAccessor, parent, that.getLevel());
            left.setNodeSplitter(nodeSplitter);
            while (size() > 0)
                left.addBlobPointer(removeBlobPointer(0));
            addBlobPointer(left.that);
            addBlobPointer(right.that);
            that.setLevel(that.getLevel() + 1);
        }
    }

    /**
     *
     * @return true if the root has been updated
     * @throws IOException
     * @throws CryptoException
     */
    private void balance() throws IOException, CryptoException {
        nodeSplitter.reset();
        int size = size();
        for (int i = 0; i < size; i++) {
            IChunkPointer child = get(i);
            nodeSplitter.write(child.getBoxPointer().getDataHash().getBytes());
            if (nodeSplitter.isTriggered()) {
                if (i == size - 1) // all good
                    return;
                // split left over into a right node
                splitAt(i + 1);
                return;
            }
        }

        // we are not full; get pointers from the right neighbour till we are full
        ChunkContainerNode neighbour = findRightNeighbour();
        if (neighbour != null) {
            while (neighbour.size() > 0) {
                ChunkContainerNode nextNeighbour = null;
                // we need one item to find the next right neighbour
                if (neighbour.size() == 1)
                    nextNeighbour = neighbour.findRightNeighbour();

                IChunkPointer pointer = neighbour.removeBlobPointer(0, true);
                addBlobPointer(pointer);
                nodeSplitter.write(pointer.getBoxPointer().getDataHash().getBytes());
                if (nodeSplitter.isTriggered()) {
                    // if the parent is the root node check if the root node is redundant else we are done
                    if (getParent() != null && getParent().getParent() == null)
                        break;
                    else
                        return;
                }

                if (nextNeighbour != null)
                    neighbour = nextNeighbour;
            }
        }

        // Since we merged all right neighbours in; we have to check if the root is redundant
        if (getParent() != null && getParent().size() == 1) {
            int level = that.getLevel();
            ChunkContainerNode root = getRoot();
            root.removeBlobPointer(0);
            while (size() > 0)
                root.addBlobPointer(removeBlobPointer(0));

            root.that.setLevel(level);
        }
    }

    private ChunkContainerNode getRoot() {
        ChunkContainerNode root = this;
        while (root.getParent() != null)
            root = root.getParent();
        return root;
    }

    /**
     * The idea is to flush item from the left to the right
     *
     * @param childOnly only child nodes are flushed
     * @throws IOException
     */
    public void flush(boolean childOnly) throws IOException, CryptoException {
        int level = that.getLevel();
        if (level > LEAF_LEVEL) {
            // IMPORTANT: the slot size may grow when flushing the child so check in each iteration!
            for (int i = 0; i < slots.size(); i++) {
                IChunkPointer pointer = slots.get(i);
                ChunkContainerNode blob = (ChunkContainerNode) pointer.getCachedChunk();
                if (blob == null || blob.onDisk)
                    continue;
                blob.flush(false);
                if (that.getLevel() != level) {
                    // only the root node can change its level
                    assert getParent() == null;
                    flush(childOnly);
                    return;
                }
            }
        }

        if (!childOnly) {
            balance();
            if (that.getLevel() != level) {
                // only the root node can change its level
                assert getParent() == null;
                flush(childOnly);
                return;
            }
            byte[] data = getData();
            HashValue oldBoxHash = that.getBoxPointer().getBoxHash();
            HashValue boxHash = blobAccessor.putChunk(data).key;
            // cleanup old chunk
            if (!boxHash.equals(oldBoxHash) && !oldBoxHash.isZero())
                blobAccessor.releaseChunk(oldBoxHash);

            if (parent != null)
                parent.invalidate();

            that.setBoxPointer(new BoxPointer(hash(), boxHash));

            onDisk = true;
        }
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
        String string = "Data Hash: " + hash() + "\n";
        for (IChunkPointer pointer : slots)
            string += pointer.toString() + "\n";
        return string;
    }

    protected String printAll() throws Exception {
        String string = toString();

        if (that.getLevel() == LEAF_LEVEL)
            return string;
        for (IChunkPointer pointer : slots)
            string += ((ChunkContainerNode)getBlob(pointer)).printAll();
        return string;
    }

    @Override
    public HashValue hash() {
        if (dataHash == null)
            dataHash = calculateDataHash();

        return calculateDataHash();
    }

    public BoxPointer getBoxPointer() {
        return that.getBoxPointer();
    }

    private HashValue calculateDataHash() {
        MessageDigest messageDigest = null;
        try {
            messageDigest = CryptoHelper.sha256Hash();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        for (IChunkPointer pointer : slots)
            messageDigest.update(pointer.getBoxPointer().getDataHash().getBytes());
        return new HashValue(messageDigest.digest());
    }

    protected void invalidate() {
        data = null;
        dataHash = null;
        onDisk = false;
        if (parent != null)
            parent.invalidate();
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

    protected IChunkPointer removeBlobPointer(int i) {
        return removeBlobPointer(i, false);
    }

    protected IChunkPointer removeBlobPointer(int i, boolean updateParentsIfEmpty) {
        IChunkPointer pointer = slots.remove(i);
        invalidate();
        if (updateParentsIfEmpty && parent != null && slots.size() == 0) {
            int inParentIndex = parent.indexOf(that);
            parent.removeBlobPointer(inParentIndex, true);
        }
        return pointer;
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
