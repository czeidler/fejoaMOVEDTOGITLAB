/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


public class Config {
    static public HashValue newBoxHash() {
        return new HashValue(HashValue.HASH_SIZE);
    }

    static public HashValue newDataHash() {
        return new HashValue(HashValue.HASH_SIZE);
    }
}
