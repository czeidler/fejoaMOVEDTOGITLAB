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
    final static public long MASK_64B = 0x3F;
    final static public long MASK_128B = 0x7F;
    final static public long MASK_256B = 0xFF;
    final static public long MASK_8KB = 0x1FFF;
    final private long mask;
    final private int windowSize = 48;

    public RabinSplitter(long mask) {
        this.mask = mask;
        Polynomial irreduciblePolynomial = Polynomial.createFromLong(9256118209264353l);
        window = new RabinFingerprintLongWindowed(irreduciblePolynomial, windowSize);
    }

    public RabinSplitter() {
        this(MASK_8KB);
    }

    @Override
    protected boolean updateInternal(byte i) {
        window.pushByte(i);
        if ((~window.getFingerprintLong() & mask) == 0)
            return true;
        return false;
    }

    @Override
    protected void resetInternal() {
        window.reset();
    }

    @Override
    public ChunkSplitter newInstance() {
        return new RabinSplitter(mask);
    }
}
