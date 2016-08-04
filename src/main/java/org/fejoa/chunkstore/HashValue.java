/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.nio.ByteBuffer;
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

    static public HashValue fromHex(String hash) {
        return new HashValue(CryptoHelper.fromHex(hash));
    }

    @Override
    public int hashCode() {
        return ByteBuffer.wrap(hash).getInt();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HashValue))
            return false;
        return Arrays.equals(hash, ((HashValue) o).hash);
    }

    public boolean isZero() {
        for (int i = 0; i < hash.length; i++) {
            if (hash[i] != 0)
                return false;
        }
        return true;
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
