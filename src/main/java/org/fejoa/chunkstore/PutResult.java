/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


/**
 * Result type for an insertion into a hash like table.
 *
 * @param <Type> key of the inserted data
 */
public class PutResult<Type> {
    final public Type key;
    // indicate if the data was already in the database
    final public boolean wasInDatabase;

    public PutResult(Type key, boolean wasInDatabase) {
        this.key = key;
        this.wasInDatabase = wasInDatabase;
    }
}
