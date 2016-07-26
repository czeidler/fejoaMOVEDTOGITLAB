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


public class RabinSplitter extends ChunkSplitter {
    final private RabinFingerprintLongWindowed window;

    final static public int CHUNK_1KB = 1024;
    final static public int CHUNK_8KB = 8 * CHUNK_1KB;
    final static public int CHUNK_16KB = 16 * CHUNK_1KB;
    final static public int CHUNK_32KB = 32 * CHUNK_1KB;
    final static public int CHUNK_64KB = 64 * CHUNK_1KB;
    final static public int CHUNK_128KB = 128 * CHUNK_1KB;
    final static private long MASK = 0xFFFFFFFFL;

    final private long targetChunkSize;
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
        Polynomial irreduciblePolynomial = Polynomial.createFromLong(9256118209264353l);
        window = new RabinFingerprintLongWindowed(irreduciblePolynomial, windowSize);
    }

    private RabinSplitter(RabinSplitter splitter) {
        this.targetChunkSize = splitter.targetChunkSize;
        this.minChunkSize = splitter.minChunkSize;
        this.maxChunkSize = splitter.maxChunkSize;
        this.window = new RabinFingerprintLongWindowed(splitter.window);
    }

    public RabinSplitter() {
        this(CHUNK_8KB, 128);
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
