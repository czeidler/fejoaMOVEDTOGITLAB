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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class BaseBPlusTree<IndexType extends Number, DataType extends Number> {
    public interface IDataType<Type> {
        short size();

        Type fromLong(Long value);

        Long toLong(Type value);

        void write(DataOutput file, Type value) throws IOException;

        Type read(DataInput file) throws IOException;
    }

    static public class IntegerType implements IDataType<Integer> {
        @Override
        public short size() {
            return 4;
        }

        @Override
        public Integer fromLong(Long value) {
            return value.intValue();
        }

        @Override
        public Long toLong(Integer value) {
            return value.longValue();
        }

        @Override
        public void write(DataOutput file, Integer value) throws IOException {
            file.writeInt(value);
        }

        @Override
        public Integer read(DataInput file) throws IOException {
            return file.readInt();
        }
    }

    static public class LongType implements IDataType<Long> {
        @Override
        public short size() {
            return 8;
        }

        @Override
        public Long fromLong(Long value) {
            return value;
        }

        @Override
        public Long toLong(Long value) {
            return value;
        }

        @Override
        public void write(DataOutput file, Long value) throws IOException {
            file.writeLong(value);
        }

        @Override
        public Long read(DataInput file) throws IOException {
            return file.readLong();
        }
    }

    class Tile {
        final long index;

        public Tile(long index) {
            this.index = index;
        }

        private void seekTo() throws IOException {
            // index start at 1
            Long offset = tileSize * (index - 1);
            file.seek(dataStart() + offset);
        }

        public void write(byte[] data) throws IOException {
            if (data.length != tileSize)
                throw new IOException("Data tile size mismatch");
            seekTo();
            file.write(data);
        }

        public byte[] read() throws IOException {
            seekTo();
            byte[] data = new byte[tileSize];
            file.readFully(data);
            return data;
        }
    }

    class TileAllocator {
        long currentFreedTail = 0L;
        long currentFreedHead = 0L;
        Tile alloc() throws IOException {
            if (freeTileList != 0) {
                DeletedNode deletedNode = new DeletedNode(freeTileList);
                freeTileList = deletedNode.readDeletedPointer();
                return deletedNode.tile;
            }
            // TODO: check if max number of tiles has been reached
            Long dataSize = file.length() - dataStart();
            file.setLength(file.length() + tileSize);
            return new Tile(dataSize / tileSize + 1);
        }

        void free(Tile tile) throws IOException {
            new DeletedNode(tile).writeDeletedPointer(currentFreedHead);
            currentFreedHead = tile.index;
            if (currentFreedTail == 0L)
                currentFreedTail = currentFreedHead;
        }

        void commit() throws IOException {
            if (currentFreedTail != 0L)
                new DeletedNode(currentFreedTail).writeDeletedPointer(freeTileList);

            freeTileList = currentFreedHead;

            currentFreedHead = 0L;
            currentFreedTail = 0L;
        }

        int countDeletedTiles() throws IOException {
            int i = 0;
            long current = freeTileList;
            while (current != 0) {
                i++;
                DeletedNode deletedNode = new DeletedNode(current);
                current = deletedNode.readDeletedPointer();
            }

            return i;
        }
    }

    class TileNode {
        protected Tile tile;
        protected boolean writeable = true;

        public TileNode(Tile tile) {
            this.tile = tile;
        }


        public Long getIndex() {
            return tile.index;
        }

        public void onNodeRead() {
            // When we read the node from disk we can modify it anymore
            writeable = false;
        }

        public void checkWriteable() throws IOException {
            if (!writeable)
                throw new IOException("Tile not writeable");
        }

        public void prepareForWrite() throws IOException {
            if (writeable)
                return;

            tileAllocator.free(tile);
            tile = tileAllocator.alloc();
            writeable = true;
        }
    }

    class Node extends TileNode {
        final int nodeDepth;
        protected Node parent;
        // index of the node in the parent node
        final private int pointerIndexInParent;
        final int maxNumberOfKeys;
        long deletedPointer;
        final List<byte[]> keys;
        final List<IndexType> pointers;

        public Node(Node parent, int pointerIndexInParent, Tile tile) {
            super(tile);
            if (parent == null)
                this.nodeDepth = 1;
            else
                this.nodeDepth = parent.nodeDepth + 1;
            this.parent = parent;
            this.pointerIndexInParent = pointerIndexInParent;

            maxNumberOfKeys = nKeysPerTile();
            keys = new ArrayList<>(maxNumberOfKeys);
            pointers = new ArrayList<>(maxNumberOfKeys + 1);
        }

        public Node getParent() {
            return parent;
        }

        public int getPointerIndexInParent() {
            return pointerIndexInParent;
        }

        public int getRightmostPointerIndex() {
            return keys.size();
        }

        public Node rootNode() {
            if (parent == null)
                return this;
            return parent.rootNode();
        }

        public boolean hasTooManyKeys() {
            return keys.size() > maxNumberOfKeys;
        }

        public int minNumberOfKeys() {
            return maxNumberOfKeys / 2;
        }

        public boolean hasMinNumberOfKeys() {
            return keys.size() >= minNumberOfKeys();
        }

        public boolean hasKeySurplus() {
            return keys.size() > minNumberOfKeys();
        }

        public void addRaw(IndexType p1, byte[] key) {
            pointers.add(p1);
            keys.add(key);
        }

        public void replacePointer(int index, IndexType p) {
            pointers.remove(index);
            pointers.add(index, p);
        }

        public void add(int insertPosition, IndexType p1, byte[] key, IndexType p2) {
            if (pointers.size() > insertPosition)
                pointers.remove(insertPosition);
            pointers.add(insertPosition, p1);

            keys.add(insertPosition, key);

            pointers.add(insertPosition + 1, p2);
        }

        public void addNode(IndexType p1, byte[] key, IndexType p2, Node node) {
            if (indexType.toLong(p1) != 0l) {
                if (pointers.size() > 0)
                    pointers.remove(pointers.size() - 1);
                pointers.add(p1);
            }
            if (key != null) {
                keys.add(key);
                pointers.add(p2);
            }

            for (int i = 0; i < node.keys.size(); i++) {
                keys.add(node.keys.get(i));
                pointers.add(node.pointers.get(i + 1));
            }
        }

        public void write() throws IOException {
            prepareForWrite();
            checkWriteable();
            ByteArrayOutputStream out = new ByteArrayOutputStream(tileSize);
            DataOutputStream writer = new DataOutputStream(out);
            // deleted node pointer
            indexType.write(writer, indexType.fromLong(0l));

            writeKeys(writer);

            // write tile to disk
            tile.write(Arrays.copyOf(out.toByteArray(), tileSize));
        }

        protected void writeKeys(DataOutputStream writer) throws IOException {
            boolean writeFinish = false;
            // pointer and keys
            for (int i = 0; i < maxNumberOfKeys; i++) {
                if (i == keys.size()) {
                    // write finishing entry
                    writeFinish = true;
                    break;
                }
                indexType.write(writer, pointers.get(i));
                writer.write(keys.get(i));
            }
            indexType.write(writer, pointers.get(pointers.size() - 1));
            if (writeFinish) {
                writer.write(new byte[hashSize]);
                indexType.write(writer, indexType.fromLong(0l));
            }
        }

        public void read() throws IOException {
            byte[] data = tile.read();
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            DataInputStream reader = new DataInputStream(in);

            // skip deleted pointer
            deletedPointer = indexType.toLong(indexType.read(reader));

            readKeys(reader);

            onNodeRead();
        }

        private boolean isEmpty(byte[] array) {
            int sum = 0;
            for (byte b : array)
                sum |= b;
            return sum == 0;
        }

        protected void readKeys(DataInputStream reader) throws IOException {
            IndexType pointer = indexType.read(reader);
            if (indexType.toLong(pointer) > 0) {
                pointers.add(pointer);
                for (int i = 0; i < maxNumberOfKeys; i++) {
                    byte[] key = new byte[hashSize];
                    reader.readFully(key);
                    pointer = indexType.read(reader);
                    if (isEmpty(key) && indexType.toLong(pointer) == 0l)
                        break;

                    keys.add(key);
                    pointers.add(pointer);
                }
            }
        }

        public void findPosition(BigInteger key, SearchResult result) {
            result.foundKey = null;
            for (int i = 0; i < keys.size(); i++) {
                BigInteger current = new BigInteger(keys.get(i));
                result.keyComparison = current.compareTo(key);
                if (result.keyComparison > 0) {
                    result.foundKey = current;
                    result.keyPosition = i;
                    break;
                } else if (result.keyComparison == 0) {
                    result.foundKey = current;
                    result.keyPosition = i + 1;
                    result.leftAnchor = this;
                    result.leftAnchorPointer = i;
                    break;
                }
            }
            if (result.foundKey == null)
                result.keyPosition = keys.size();
        }

        public Node readChildNode(int pointerIndex) throws IOException {
            int nodeDepth = getDepth();
            long pointer = indexType.toLong(pointers.get(pointerIndex));
            if (nodeDepth + 1 == depth)
                return readLeafNode(pointer, this, pointerIndex);
            return readNode(pointer, this, pointerIndex);
        }

        class SplitResult {
            byte[] key;
            Node newNode;
        }

        public SplitResult split() throws IOException {
            SplitResult result = new SplitResult();
            // split
            result.newNode = new Node(parent, -1, tileAllocator.alloc());

            int splitPoint = keys.size() / 2;
            // insert item to new node
            for (int i = splitPoint + 1; i < keys.size(); i++)
                result.newNode.addRaw(pointers.get(i), keys.get(i));
            result.newNode.pointers.add(pointers.get(keys.size()));
            result.key = keys.get(splitPoint);
            // remove items from old node
            while (keys.size() > splitPoint) {
                keys.remove(splitPoint);
                pointers.remove(splitPoint + 1);
            }
            return result;
        }

        public int getDepth() {
            return nodeDepth;
        }
    }

    class DeletedNode extends Node {
        public DeletedNode(long tileIndex) {
            super(null, 0, new Tile(tileIndex));
        }

        public DeletedNode(Tile tile) {
            super(null, 0, tile);
        }

        public void writeDeletedPointer(long pointer) throws IOException {
            tile.seekTo();
            indexType.write(file, indexType.fromLong(pointer));
        }

        public long readDeletedPointer() throws IOException {
            tile.seekTo();
            return indexType.toLong(indexType.read(file));
        }
    }

    class LeafNode extends Node {
        public LeafNode(Node parent, int indexInParent, Tile tile) {
            super(parent, indexInParent, tile);
        }

        public long getNextPointer() {
            return indexType.toLong(pointers.get(pointers.size() - 1));
        }

        public void add(int index, IndexType p1, byte[] key) {
            pointers.add(index, p1);
            keys.add(index, key);
        }

        public void remove(int index) {
            pointers.remove(index);
            keys.remove(index);
        }

        @Override
        public void add(int insertPosition, IndexType p1, byte[] key, IndexType p2) {
            assert indexType.toLong(p2) == 0l;

            pointers.add(insertPosition, p1);
            keys.add(insertPosition, key);
            // add next node when
            if (pointers.size() == 1)
                pointers.add(p2);
        }

        @Override
        public void addNode(IndexType p1, byte[] key, IndexType p2, Node node) {
            assert indexType.toLong(p2) == 0l;
            if (pointers.size() > 0)
                pointers.remove(pointers.size() - 1);

            if (indexType.toLong(p1) != 0l) {
                pointers.add(p1);
                keys.add(key);
            }

            for (int i = 0; i < node.keys.size(); i++) {
                pointers.add(node.pointers.get(i));
                keys.add(node.keys.get(i));
            }

            // next pointer
            if (node.pointers.size() > 0)
                pointers.add(node.pointers.get(node.pointers.size() - 1));
        }

        @Override
        public void findPosition(BigInteger key, SearchResult result) {
            result.foundKey = null;
            for (int i = 0; i < keys.size(); i++) {
                BigInteger current = new BigInteger(keys.get(i));
                result.keyComparison = current.compareTo(key);
                if (result.keyComparison >= 0) {
                    result.foundKey = current;
                    result.keyPosition = i;
                    break;
                }
            }
            if (result.foundKey == null)
                result.keyPosition = keys.size();
        }

        @Override
        public SplitResult split() throws IOException {
            SplitResult result = new SplitResult();
            // split
            result.newNode = new LeafNode(parent, 0, tileAllocator.alloc());

            int splitPoint = keys.size() / 2;
            // insert item to new node
            for (int i = splitPoint; i < keys.size(); i++)
                result.newNode.addRaw(pointers.get(i), keys.get(i));
            result.newNode.pointers.add(pointers.get(keys.size()));

            result.key = keys.get(splitPoint);
            // remove items from old node
            while (keys.size() > splitPoint) {
                keys.remove(splitPoint);
                pointers.remove(splitPoint);
            }
            assert pointers.size() - 1 == splitPoint;
            pointers.remove(pointers.size() - 1);

            // next pointer
            pointers.add(indexType.fromLong(result.newNode.tile.index));
            return result;
        }

        @Override
        public Node readChildNode(int pointerIndex) throws IOException {
            return null;
        }
    }

    private int tileSize = 1024;
    private short version = 1;
    private short hashSize;
    private short depth = 1;
    // Tile count starts at 1. Tile 0 is an invalid tile.
    private long rootTileIndex = 0l;
    private long freeTileList = 0l;

    final private RandomAccessFile file;
    final private IDataType<IndexType> indexType;
    final private IDataType<DataType> dataType;
    final private TileAllocator tileAllocator;

    public BaseBPlusTree(RandomAccessFile file, IDataType<IndexType> indexType, IDataType<DataType> dataType) {
        this.file = file;
        this.indexType = indexType;
        this.dataType = dataType;
        this.tileAllocator = new TileAllocator();
    }

    public short getDepth() {
        return depth;
    }

    public IDataType<IndexType> getIndexType() {
        return indexType;
    }

    public IDataType<DataType> getDataType() {
        return dataType;
    }

    private int nKeysPerTile() {
        return (tileSize - 2 * indexType.size()) / (indexType.size() + hashSize);
    }

    public void create(int hashSize, int tileSize) throws IOException {
        this.hashSize = (short)hashSize;
        this.tileSize = tileSize;

        file.setLength(0);
        writeHeader();
    }

    public void open() throws IOException {
        readHeader();
    }

    private void readHeader() throws IOException {
        file.seek(0);
        version = file.readShort();
        hashSize = file.readShort();
        tileSize = file.readInt();
        rootTileIndex = indexType.toLong(indexType.read(file));
        depth = file.readShort();
        freeTileList = indexType.toLong(indexType.read(file));
    }

    private void writeHeader() throws IOException {
        file.seek(0);
        file.writeShort(version);
        file.writeShort(hashSize);
        file.writeInt(tileSize);
        indexType.write(file, indexType.fromLong(rootTileIndex));
        file.writeShort(depth);
        indexType.write(file, indexType.fromLong(freeTileList));
    }

    private long headerSize() {
        // version + hash size + tileSize + root tile + depth free tile list
        return 2 * 4 + 8 + indexType.size() + 4 + indexType.size();
    }

    public void printHeader() {
        System.out.println("Version: " + version + ", Hash size: " + hashSize + ", Tile size: " + tileSize
                + ", Root index: " + rootTileIndex + ", Depth: " + depth + ", Free Tiles: " + freeTileList);
    }

    private void printNode(Node node, boolean compact) {
        if (!compact)
            System.out.print(" d:" + node.deletedPointer);

        for (int i = 0; i < node.keys.size(); i++)
            System.out.print(node.pointers.get(i) + "|" + CryptoHelper.toHex(node.keys.get(i)) + "|");
        if (node.pointers.size() > node.keys.size())
            System.out.print(node.pointers.get(node.pointers.size() - 1));

        System.out.print("@" + node.tile.index);
    }

    private void printTree(List<Node> level, int currentDepth, boolean compact) throws IOException {
        if (level.size() == 0)
            return;
        List<Node> nextLevel = new ArrayList<>();
        for (Node node : level) {
            printNode(node, compact);
            System.out.print(" ");

            if (currentDepth == depth)
                continue;

            for (int i = 0; i < node.pointers.size(); i++) {
                Node child = readNode(indexType.toLong(node.pointers.get(i)), null, 0);
                nextLevel.add(child);
            }
        }
        System.out.println();
        printTree(nextLevel, currentDepth + 1, compact);
    }

    public void printTree(boolean compact) throws IOException {
        Node rootNode = readRootNode();
        printTree(Collections.singletonList(rootNode), 1, compact);
    }

    public void print() throws IOException {
        print(true);
    }

    public void print(boolean compact) throws IOException {
        printHeader();
        printTree(compact);
    }

    private long dataStart() {
        return headerSize();
    }

    private void commit(Node rootNode) throws IOException {
        commit(rootNode.getIndex());
    }

    private void commit(long rootNodeIndex) throws IOException {
        this.tileAllocator.commit();
        this.rootTileIndex = rootNodeIndex;

        writeHeader();
    }

    public int countDeletedTiles() throws IOException {
        return tileAllocator.countDeletedTiles();
    }

    private void insert(Node insertNode, int insertPosition, BigInteger key, IndexType p1, byte[] rawKey, IndexType p2)
            throws IOException {
        insertNode.add(insertPosition, p1, rawKey, p2);

        if (insertNode.keys.size() > insertNode.maxNumberOfKeys) {
            // split
            Node.SplitResult result = insertNode.split();

            Node parent = insertNode.parent;
            if (parent == null) {
                // new root
                parent = new Node(null, 0, tileAllocator.alloc());
                insertNode.parent = parent;
                result.newNode.parent = parent;
                depth++;
            }
            result.newNode.write();
            insertNode.write();

            SearchResult inNodePosition = new SearchResult();
            inNodePosition.node = parent;
            parent.findPosition(key, inNodePosition);
            insert(parent, inNodePosition.keyPosition, new BigInteger(result.key),
                    indexType.fromLong(insertNode.tile.index), result.key,
                    indexType.fromLong(result.newNode.tile.index));
        } else {
            insertNode.write();
            // update parent nodes
            updateParentNodes(insertNode);
        }
    }

    private void updateParentNodes(Node left, Node right) throws IOException {
        while (left.parent != right.parent) {
            Node leftParent = left.parent;
            Node rightParent = right.parent;
            leftParent.replacePointer(left.pointerIndexInParent, indexType.fromLong(left.tile.index));
            rightParent.replacePointer(right.pointerIndexInParent, indexType.fromLong(right.tile.index));
            leftParent.write();
            rightParent.write();
            left = leftParent;
            right = rightParent;
        }
        // update anchor
        assert left.parent == right.parent;
        Node anchor = left.parent;
        anchor.replacePointer(left.pointerIndexInParent, indexType.fromLong(left.tile.index));
        anchor.replacePointer(right.pointerIndexInParent, indexType.fromLong(right.tile.index));
        anchor.write();

        updateParentNodes(anchor);
    }

    private void updateParentNodes(Node node) throws IOException {
        Node current = node;
        while (current.parent != null) {
            Node parent = current.parent;
            parent.replacePointer(current.pointerIndexInParent, indexType.fromLong(current.tile.index));
            parent.write();
            current = parent;
        }
    }

    public boolean put(HashValue hash, DataType address) throws IOException {
        assert hash.size() == hashSize;

        BigInteger key = new BigInteger(hash.getBytes());
        SearchResult result = find(key);
        if (result.foundKey != null && result.keyComparison == 0) {
            // TODO replace
            throw new IOException("replacing not supported yet");
            //return false;
        }
        insert(result.node, result.keyPosition, key, indexType.fromLong(dataType.toLong(address)), hash.getBytes(),
                indexType.fromLong(0l));

        commit(result.node.rootNode());
        return true;
    }

    public DataType get(String hash) throws IOException {
        return get(CryptoHelper.fromHex(hash));
    }

    class SearchResult {
        public Node node;
        public int keyPosition;
        public BigInteger foundKey;
        public int keyComparison;
        // Fields if key occurs in a non-leaf node:
        public Node leftAnchor;
        public int leftAnchorPointer;

        public boolean isExactMatch() {
            return foundKey != null && keyComparison == 0;
        }
    }

    private SearchResult find(BigInteger key) throws IOException {
        SearchResult result = new SearchResult();
        result.node = readRootNode();
        // find node to insert
        for (int a = 0; a < depth; a++) {
            if (result.node.keys.size() == 0) {
                if (depth != 1)
                    throw new IOException("Unexpected empty node");
                break;
            }
            result.node.findPosition(key, result);
            // if not on leaf level, read the next node
            if (a < depth - 1) {
                long nextNode = indexType.toLong(result.node.pointers.get(result.keyPosition));
                if (nextNode == 0l)
                    throw new IOException("Invalid pointer");
                if (a == depth - 2)
                    result.node = readLeafNode(nextNode, result.node, result.keyPosition);
                else
                    result.node = readNode(nextNode, result.node, result.keyPosition);
            }
        }
        return result;
    }

    public DataType get(byte[] hash) throws IOException {
        assert hash.length == hashSize;

        BigInteger key = new BigInteger(hash);
        SearchResult result = find(key);
        if (result.foundKey == null)
            return null;
        if (result.keyComparison != 0)
            return null;
        return dataType.fromLong(indexType.toLong(result.node.pointers.get(result.keyPosition)));
    }

    private Node readNode(long index, Node parent, int inParentIndex) throws IOException {
        Tile tile = new Tile(index);
        Node node = new Node(parent, inParentIndex, tile);
        node.read();
        return node;
    }

    private LeafNode readLeafNode(long index, Node parent, int inParentIndex) throws IOException {
        Tile tile = new Tile(index);
        LeafNode node = new LeafNode(parent, inParentIndex, tile);
        node.read();
        return node;
    }

    private Node readRootNode() throws IOException {
        Node rootNode;
        if (rootTileIndex == 0) {
            Tile tile = tileAllocator.alloc();
            rootNode = new LeafNode(null, 0, tile);
        } else if (depth == 1)
            rootNode = readLeafNode(rootTileIndex, null, 0);
        else
            rootNode = readNode(rootTileIndex, null, 0);

        return rootNode;
    }

    class FindNeighbourResult {
        public LeafNode node;
        public Node anchor;
        public int anchorKeyIndex;
    }

    private FindNeighbourResult findNeighbour(final Node node, final boolean clockwise) throws IOException {
        FindNeighbourResult result = new FindNeighbourResult();

        int rootDistance = 1;
        Node current = node;
        Node parent = node.getParent();
        while ((clockwise && current.getPointerIndexInParent() == parent.getRightmostPointerIndex())
                || (!clockwise && current.getPointerIndexInParent() == 0)) {
            current = parent;
            parent = parent.parent;
            // no neighbour?
            if (parent == null)
                return null;
            rootDistance++;
        }
        int downwardsIndex = current.getPointerIndexInParent();
        result.anchor = parent;
        Node leaf = parent;
        for (int i = 0; i < rootDistance; i++) {
            if (!clockwise) {
                // anti clockwise: first go one node left and then always right
                if (i == 0) {
                    result.anchorKeyIndex = downwardsIndex - 1;
                    downwardsIndex -= 1;
                } else
                    downwardsIndex = leaf.getRightmostPointerIndex();
            } else {
                if (i == 0) {
                    result.anchorKeyIndex = downwardsIndex;
                    downwardsIndex += 1;
                } else
                    downwardsIndex = 0;
            }

            leaf = leaf.readChildNode(downwardsIndex);
        }

        if (clockwise)
            leaf = findLeftLeafNode(leaf);
        else
            leaf = findRightLeafNode(leaf);

        result.node = (LeafNode)leaf;
        return result;
    }

    private LeafNode findLeafNode(Node node, boolean left) throws IOException {
        // go to the leaf node
        for (int i = 0; i < depth; i++) {
            int index = 0;
            if (!left)
                index = node.getRightmostPointerIndex();
            Node child = node.readChildNode(index);
            if (child == null)
                break;
            node = child;
        }
        return (LeafNode)node;
    }

    private LeafNode findLeftLeafNode(Node node) throws IOException {
        return findLeafNode(node, true);
    }

    private LeafNode findRightLeafNode(Node node) throws IOException {
        return findLeafNode(node, false);
    }

    private FindNeighbourResult findLeftNeighbour(Node node) throws IOException {
        return findNeighbour(node, false);
    }

    private FindNeighbourResult findRightNeighbour(Node node) throws IOException {
        return findNeighbour(node, true);
    }

    private void remove(Node node, int keyIndex, IndexType nodeChild) throws IOException {
        // remove key
        node.keys.remove(keyIndex);
        node.pointers.remove(keyIndex);
        if (indexType.toLong(nodeChild) != 0l) {
            node.pointers.remove(keyIndex);
            node.pointers.add(keyIndex, nodeChild);
        }

        if (node.hasMinNumberOfKeys() || (node.getParent() == null && node.keys.size() > 1)) {
            node.write();
            updateParentNodes(node);
            commit(node.rootNode());
            return;
        }
        // empty root node?
        if (node.keys.size() == 0 && node.getParent() == null) {
            if (depth == 1) {
                // root node
                node.write();
                commit(node);
                return;
            } else {
                depth--;
                assert indexType.toLong(nodeChild) != 0l;
                commit(indexType.toLong(nodeChild));
                return;
            }
        }

        FindNeighbourResult leftNeighbour = findLeftNeighbour(node);
        FindNeighbourResult rightNeighbour = findRightNeighbour(node);
        if ((leftNeighbour != null && leftNeighbour.node.hasKeySurplus())
                || (rightNeighbour != null && rightNeighbour.node.hasKeySurplus())) {
            LeafNode left;
            LeafNode right;
            FindNeighbourResult neighbour;
            if ((rightNeighbour == null && leftNeighbour != null)
                    || leftNeighbour.node.keys.size() >= rightNeighbour.node.keys.size()) {
                // get keys from the the left
                left = leftNeighbour.node;
                right = findRightLeafNode(node);
                neighbour = leftNeighbour;
                int numberOfKeys = left.keys.size() + right.keys.size();
                while (left.keys.size() > numberOfKeys / 2) {
                    int lastLeft = left.keys.size() - 1;
                    right.add(0, left.pointers.get(lastLeft), left.keys.get(lastLeft));
                    left.remove(lastLeft);
                }
            } else {
                // get keys from the the right
                left = findRightLeafNode(node);
                right = rightNeighbour.node;
                neighbour = rightNeighbour;
                int numberOfKeys = left.keys.size() + right.keys.size();
                while (right.keys.size() > numberOfKeys / 2) {
                    left.add(left.keys.size(), right.pointers.get(0), right.keys.get(0));
                    right.remove(0);
                }
            }
            Node anchor = neighbour.anchor;
            anchor.keys.remove(neighbour.anchorKeyIndex);
            anchor.keys.add(neighbour.anchorKeyIndex, right.keys.get(0));

            left.write();
            right.write();
            updateParentNodes(left, right);
            commit(anchor.rootNode());
            return;
        } else {
            // mergeSimple
            LeafNode left;
            LeafNode right;
            FindNeighbourResult neighbour;
            if (rightNeighbour == null
                    || (leftNeighbour != null && leftNeighbour.node.getParent() == node.getParent())) {
                left = leftNeighbour.node;
                right = findLeftLeafNode(node);
                neighbour = leftNeighbour;
            } else {
                left = findRightLeafNode(node);
                right = rightNeighbour.node;
                neighbour = rightNeighbour;
            }


            MergeResult result = mergeToAnchor(left, right);
            assert result.key == null && result.right == null;
            Node anchor = result.left.getParent();
            // remove next
            remove(anchor, neighbour.anchorKeyIndex, indexType.fromLong(result.left.getIndex()));
        }
    }

    class MergeResult {
        Node left;
        byte[] key = null;
        Node right;
    }

    private MergeResult mergeToAnchor(LeafNode leftLeaf, LeafNode rightLeaf) throws IOException {
        MergeResult result = new MergeResult();

        Node left = leftLeaf;
        Node right = rightLeaf;
        IndexType p1 = indexType.fromLong(0l);
        IndexType p2 = indexType.fromLong(0l);
        do {
            left.addNode(p1, result.key, p2, right);
            tileAllocator.free(right.tile);
            if (!left.hasTooManyKeys()) {
                left.write();
                p1 = indexType.fromLong(left.getIndex());
                result.key = null;
                p2 = indexType.fromLong(0l);
                result.left = left;
                result.right = null;
            } else {
                Node.SplitResult splitResult = left.split();
                assert result.key != null;
                left.write();
                right = splitResult.newNode;
                right.write();

                p1 = indexType.fromLong(left.getIndex());
                result.key = splitResult.key;
                p2 = indexType.fromLong(right.getIndex());
                result.left = left;
                result.right = right;
            }
            left = left.getParent();
            right = right.getParent();
        } while (left != right);

        return result;
    }

    public boolean remove(String key) throws IOException {
        return remove(CryptoHelper.fromHex(key));
    }

    public boolean remove(byte[] key) throws IOException {
        BigInteger keyNumber = new BigInteger(key);
        SearchResult result = find(keyNumber);
        if (!result.isExactMatch())
            return false;

        remove(result.node, result.keyPosition, indexType.fromLong(0l));
        return true;
    }
}
