/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;


/**
 * Map disk structure:
 *
 * ------------------
 * Header
 * ------------------
 * directory depth 1
 * ------------------
 * bucket depth 1
 * ------------------
 * directory depth 2
 * ------------------
 * bucket depth 2
 * ------------------
 * ...
 *
 * Each directory just contains a list of bucket indices. Each directory and bucket tile is twice as long as the
 * previous directory and bucket tile, respectively.
 *
 * Directory structure:
 * --------------
 * bucket index 0
 * bucket index 1
 * ....
 * bucket index n
 * --------------
 *
 * Bucket tile structure:
 * --------
 * bucket 0
 * bucket 1
 * ....
 * bucket n
 * --------
 *
 * Each bucket has the following structure:
 * -----------------
 * number of entries
 * -----------------
 * hash 0  |  data 0
 * hash 1  |  data 1
 * .....
 * hash n  |  data n
 * -----------------
 *
 */
public class AbstractExtensibleHashMap<IndexType extends Number, DataType extends Number> {
    public interface IDataType<Type> {
        short size();
        void write(RandomAccessFile file, Type value) throws IOException;
        Type read(RandomAccessFile file) throws IOException;
    }

    static public class IntegerType implements IDataType<Integer> {
        @Override
        public short size() {
            return 4;
        }

        @Override
        public void write(RandomAccessFile file, Integer value) throws IOException {
            file.writeInt(value);
        }

        @Override
        public Integer read(RandomAccessFile file) throws IOException {
            return file.readInt();
        }
    }

    static public class LongType implements IDataType<Long> {
        @Override
        public short size() {
            return 8;
        }

        @Override
        public void write(RandomAccessFile file, Long value) throws IOException {
            file.writeLong(value);
        }

        @Override
        public Long read(RandomAccessFile file) throws IOException {
            return file.readLong();
        }
    }

    private class Bucket {
        final int index;
        final long bucketPosition;
        final int depth;
        final List<byte[]> hashs;
        final List<DataType> elements;

        public Bucket(int index) {
            this.index = index;
            this.depth = getDepthForIndex(this.index);
            this.bucketPosition = getBucketPosition(this.index, depth);
            this.hashs = new ArrayList<>(bucketCapacity);
            this.elements = new ArrayList<>(bucketCapacity);
        }

        public boolean add(byte[] hash, DataType element) {
            if (size() >= bucketCapacity)
                return false;
            addForce(hash, element);
            return true;
        }

        public void addForce(byte[] hash, DataType element) {
            hashs.add(hash);
            elements.add(element);
        }

        public int size() {
            return hashs.size();
        }

        private void seekTo() throws IOException {
            long position = dataStartOffset() + bucketPosition;
            file.seek(position);
        }

        public void writeBucket() throws IOException {
            seekTo();
            assert size() <= bucketCapacity;
            file.writeShort(size());
            for (int i = 0; i < size(); i++) {
                file.write(hashs.get(i));
                dataType.write(file, elements.get(i));
            }
        }

        public void readBucket() throws IOException {
            seekTo();

            short entries = file.readShort();
            for (int i = 0; i < entries; i++) {
                byte[] hash = new byte[hashSize];
                file.readFully(hash);
                hashs.add(hash);
                elements.add(dataType.read(file));
            }
        }

        public void remove(int i) {
            hashs.remove(i);
            elements.remove(i);
        }
    }

    final private IDataType<IndexType> indexType;
    final private IDataType<DataType> dataType;

    private RandomAccessFile file;
    private int offset;

    private short hashSize = 4;
    private short bucketCapacity = 1;

    public AbstractExtensibleHashMap(IDataType<IndexType> indexType, IDataType<DataType> dataType) {
        this.indexType = indexType;
        this.dataType = dataType;
    }

    public void open(RandomAccessFile file, int offset) throws IOException {
        this.file = file;
        this.offset = offset;

        readHeader();
    }

    public void create(RandomAccessFile file, int offset, short hashSize, short bucketCapacity) throws IOException {
        this.file = file;
        this.offset = offset;
        this.hashSize = hashSize;
        this.bucketCapacity = bucketCapacity;

        init();
    }

    public void print() throws IOException {
        int size = directorySize();
        System.out.println("Directory size: " + size);
        for (int i = 0; i < size; i++)
            System.out.println("" + i + " Address: " + readDirectoryEntry(i));

        for (int i = 0; i < size; i++) {
            Bucket bucket = new Bucket(i);
            bucket.readBucket();
            System.out.println("" + i + " Bucket(size " + bucket.size() + ")");
            for (int a = 0; a < bucket.size(); a++) {
                System.out.println("\t" + CryptoHelper.toHex(bucket.hashs.get(a)) + " " + bucket.elements.get(a));
            }
        }
    }

    private short bucketsInDirectorySlice(int depth) {
        short nBuckets = 2;
        if (depth > 1)
            nBuckets = (short)Math.pow(2, depth - 1);
        return nBuckets;
    }

    public int directorySize() throws IOException {
        long fileLength = file.length();
        int dataLength = (int)(fileLength - dataStartOffset() - directorySliceOffset(1));
        int entrySize = entrySize();
        assert dataLength % entrySize == 0;
        return dataLength / entrySize;
    }

    private void writeHeader() throws IOException {
        file.seek(offset);
        file.writeShort(hashSize);
        file.writeShort(bucketCapacity);
    }

    private void readHeader() throws IOException {
        file.seek(offset);
        hashSize = file.readShort();
        bucketCapacity = file.readShort();
    }

    private short headerSize() {
        return 2 * 4;
    }

    private void init() throws IOException {
        writeHeader();

        // write initial tile
        final int depth = 1;
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(new Bucket(0));
        buckets.add(new Bucket(1));

        int tileOffset = dataStartOffset() + directorySliceOffset(depth);
        int tileSize = buckets.size() * entrySize();
        if (file.length() - tileOffset < tileSize)
            file.setLength(tileOffset + tileSize);
        file.seek(tileOffset);

        for (Integer i = 0; i < buckets.size(); i++)
            writeDirectoryEntry(i, (IndexType)i);
        for (int i = 0; i < buckets.size(); i++) {
            Bucket bucket = buckets.get(i);
            bucket.writeBucket();
        }
    }

    private int bucketSize() {
        // nBuckets (short) + hashes + addresses
        return 2 + bucketCapacity * (hashSize + dataType.size());
    }

    private int indexForDepth(int depth) {
        if (depth == 1)
            return 0;
        return (int)Math.pow(2, depth - 1);
    }

    private int entrySize() {
        return indexType.size() + bucketSize();
    }

    private int dataStartOffset() {
        return offset + headerSize();
    }

    private int directorySliceOffset(int depth) {
        int sliceOffset = indexForDepth(depth) * entrySize();
        return sliceOffset;
    }

    private int getDepthForIndex(int i) {
        int current = 2;
        int depth = 1;
        while (current <= i) {
            current *= 2;
            depth++;
        }
        return depth;
    }

    private long getDirectoryEntryAddress(int i) {
        int depth = getDepthForIndex(i);
        if (depth > 1)
            i -= Math.pow(2, depth - 1);

        long offset = directorySliceOffset(depth);
        offset += i * indexType.size();
        return offset;
    }

    private IndexType readDirectoryEntry(int i) throws IOException {
        long position = dataStartOffset() + getDirectoryEntryAddress(i);
        file.seek(position);
        return indexType.read(file);
    }

    private void writeDirectoryEntry(int i, IndexType entry) throws IOException {
        long position = dataStartOffset() + getDirectoryEntryAddress(i);
        file.seek(position);
        indexType.write(file, entry);
    }

    private long getBucketPosition(int i, int depth) {
        if (depth > 1)
            i -= Math.pow(2, depth - 1);
        long offset = directorySliceOffset(depth);
        // skip bucket addresses
        offset += bucketsInDirectorySlice(depth) * indexType.size();
        // select i th bucket
        offset += i * bucketSize();
        return offset;
    }

    private int getDepth() throws IOException {
        int entries = directorySize();
        int depth = 0;
        while (entries >= 2) {
            entries /= 2;
            depth++;
        }
        return depth;
    }

    public void duplicateDirectory() throws IOException {
        long fileLength = file.length();
        long dataSize = fileLength - dataStartOffset();
        int currentSize = directorySize();
        List<IndexType> directory = new ArrayList<>(currentSize);
        for (int i = 0; i < currentSize; i++)
            directory.add(readDirectoryEntry(i));

        file.setLength(fileLength + dataSize);
        file.seek(fileLength);
        for (int i = 0; i < directory.size(); i++)
            indexType.write(file, directory.get(i));
    }

    public boolean put(String hash, DataType address) throws IOException {
        return put(CryptoHelper.fromHex(hash), address);
    }

    public DataType get(String hash) throws IOException {
        return get(CryptoHelper.fromHex(hash));
    }

    public boolean remove(String hash) throws IOException {
        return remove(CryptoHelper.fromHex(hash));
    }

    private IndexType shortHash(byte[] hash, int depth) {
        Integer shortHash = ((hash[0] << 8) | (hash[1]));
        shortHash = (shortHash & ~(0xFFFFFFFF << depth));
        return (IndexType)shortHash;
    }

    private void distributeBucket(boolean duplicate, Bucket bucket, int depth) throws IOException {
        if (duplicate) {
            duplicateDirectory();
            depth++;
        }

        Map<IndexType, Bucket> modifiedBuckets = new HashMap<>();
        for (int i = 0; i < bucket.hashs.size(); i++) {
            byte[] hash = bucket.hashs.get(i);
            DataType address = bucket.elements.get(i);
            IndexType index = shortHash(hash, depth);
            if ((Integer)index == bucket.index)
                continue;
            else {
                Bucket newBucket = modifiedBuckets.get(index);
                if (newBucket == null) {
                    newBucket = new Bucket((Integer) index);
                    assert newBucket.depth == depth;
                    modifiedBuckets.put(index, newBucket);
                }
                newBucket.addForce(hash, address);
                bucket.remove(i);
                i--;
                continue;
            }
        }
        for (Map.Entry<IndexType, Bucket> entry : modifiedBuckets.entrySet()) {
            Bucket modifiedBucket = entry.getValue();
            IndexType index = entry.getKey();
            writeDirectoryEntry((Integer) index, index);
            if (modifiedBucket.size() > bucketCapacity)
                distributeBucket(true, modifiedBucket, depth);
            else
                modifiedBucket.writeBucket();
        }
        bucket.writeBucket();
    }

    public boolean put(byte[] hash, DataType address) throws IOException {
        assert hash.length == hashSize;

        int depth = getDepth();
        int index = (Integer)shortHash(hash, depth);

        int bucketIndex = (Integer)readDirectoryEntry(index);
        Bucket bucket = new Bucket(bucketIndex);
        bucket.readBucket();
        if (bucket.add(hash, address)) {
            bucket.writeBucket();
        } else {
            // force add and then split
            bucket.addForce(hash, address);
            boolean duplicate = index == bucketIndex;
            distributeBucket(duplicate, bucket, depth);
        }

        return true;
    }

    public DataType get(byte[] hash) throws IOException {
        assert hash.length == hashSize;

        int depth = getDepth();
        int index = (Integer)shortHash(hash, depth);

        int bucketIndex = (Integer)readDirectoryEntry(index);
        Bucket bucket = new Bucket(bucketIndex);
        bucket.readBucket();

        for (int i = 0; i < bucket.size(); i++) {
            byte[] current = bucket.hashs.get(i);
            if (Arrays.equals(current, hash))
                return bucket.elements.get(i);
        }
        return null;
    }

    public boolean remove(byte[] hash) throws IOException {
        assert hash.length == hashSize;

        int depth = getDepth();
        int index = (Integer)shortHash(hash, depth);

        int bucketIndex = (Integer)readDirectoryEntry(index);
        Bucket bucket = new Bucket(bucketIndex);
        bucket.readBucket();

        for (int i = 0; i < bucket.size(); i++) {
            byte[] current = bucket.hashs.get(i);
            if (Arrays.equals(current, hash)) {
                bucket.remove(i);
                // We only need to write the bucket if there are still elements or we at depth 1 otherwise we point to
                // the bucket at depth - 1.
                if (bucket.size() == 0 && bucket.depth > 1) {
                    // point to the bucket in depth - 1
                    IndexType prevBucket = readDirectoryEntry((Integer)shortHash(hash, depth - 1));
                    writeDirectoryEntry(index, prevBucket);
                    // todo shrink idx file if possible
                } else {
                    bucket.writeBucket();
                }
                return true;
            }
        }
        return false;
    }
}
