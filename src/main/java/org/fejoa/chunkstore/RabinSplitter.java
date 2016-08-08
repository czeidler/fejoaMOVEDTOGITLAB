/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.rabinfingerprint.fingerprint.RabinFingerprintLongWindowed;
import org.rabinfingerprint.polynomial.Polynomial;

import java.util.HashMap;
import java.util.Map;


public class RabinSplitter extends ChunkSplitter {
    // Init the window is expensive so cache it this bucket and reuse it to create a new window.
    static private class WindowBucket {
        final private Map<Integer, RabinFingerprintLongWindowed> bucket = new HashMap<>();

        public RabinFingerprintLongWindowed get(int windowSize) {
            RabinFingerprintLongWindowed window = bucket.get(windowSize);
            if (window != null)
                return new RabinFingerprintLongWindowed(window);
            window = new RabinFingerprintLongWindowed(Polynomial.createFromLong(9256118209264353l), windowSize);
            bucket.put(windowSize, window);
            return window;
        }
    }

    final static private WindowBucket bucket = new WindowBucket();
    final private RabinFingerprintLongWindowed window;

    final static public int CHUNK_1KB = 1024;
    final static public int CHUNK_8KB = 8 * CHUNK_1KB;
    final static public int CHUNK_16KB = 16 * CHUNK_1KB;
    final static public int CHUNK_32KB = 32 * CHUNK_1KB;
    final static public int CHUNK_64KB = 64 * CHUNK_1KB;
    final static public int CHUNK_128KB = 128 * CHUNK_1KB;
    final static private long MASK = 0xFFFFFFFFL;

    final private int targetChunkSize;
    final private int windowSize = 48;
    private long chunkSize;
    final private int minChunkSize;
    final private int maxChunkSize;

    public RabinSplitter(int targetChunkSize, int minChunkSize) {
        this(targetChunkSize, minChunkSize, 2 * targetChunkSize);
    }

    public RabinSplitter(int targetChunkSize, int minChunkSize, int maxChunkSize) {
        this.targetChunkSize = targetChunkSize;
        this.minChunkSize = minChunkSize;
        this.maxChunkSize = maxChunkSize;
        this.window = bucket.get(windowSize);
    }

    private RabinSplitter(RabinSplitter splitter) {
        this.targetChunkSize = splitter.targetChunkSize;
        this.minChunkSize = splitter.minChunkSize;
        this.maxChunkSize = splitter.maxChunkSize;
        this.window = splitter.window;
    }

    public RabinSplitter() {
        this(CHUNK_8KB, 128);
    }

    public int getTargetChunkSize() {
        return targetChunkSize;
    }

    public int getMinChunkSize() {
        return minChunkSize;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    @Override
    protected boolean updateInternal(byte i) {
        chunkSize ++;
        if (chunkSize < minChunkSize - windowSize)
            return false;
        window.pushByte(i);
        if (chunkSize < minChunkSize)
            return false;
        if (chunkSize >= maxChunkSize)
            return true;
        if ((window.getFingerprintLong() & MASK) < MASK / targetChunkSize)
            return true;
        return false;
    }

    @Override
    protected void resetInternal() {
        chunkSize = 0;
        window.reset();
    }

    @Override
    public ChunkSplitter newInstance() {
        return new RabinSplitter(this);
    }
}
