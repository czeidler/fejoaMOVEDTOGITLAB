/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.util.Arrays;


public class HashValue {
    final public static short HASH_SIZE = 32;
    final private byte[] hash;

    public HashValue(byte[] hash) {
        this.hash = hash;
    }

    public HashValue(int hashSize) {
        this.hash = new byte[hashSize];
    }

    public HashValue(HashValue hash) {
        this.hash = Arrays.copyOf(hash.getBytes(), hash.size());
    }

    public byte[] getBytes() {
        return hash;
    }

    public int size() {
        return hash.length;
    }

    public String toHex() {
        return CryptoHelper.toHex(hash);
    }

    @Override
    public String toString() {
        return toHex();
    }
}
