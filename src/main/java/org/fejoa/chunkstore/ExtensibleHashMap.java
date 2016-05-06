/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


public class ExtensibleHashMap extends AbstractExtensibleHashMap<Integer, Long> {
    public ExtensibleHashMap() {
        super(new IntegerType(), new LongType());
    }
}
