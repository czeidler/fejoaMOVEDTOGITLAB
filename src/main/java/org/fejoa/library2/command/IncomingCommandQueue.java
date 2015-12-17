/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.fejoa.library2.database.StorageDir;

import java.io.IOException;


public class IncomingCommandQueue extends CommandQueue<CommandQueue.Entry> {
    public IncomingCommandQueue(StorageDir dir) throws IOException {
        super(dir);
    }

    @Override
    protected Entry instantiate() {
        return new Entry();
    }

    public void addCommand(byte[] bytes) throws IOException {
        addCommand(new Entry(bytes));
    }
}
