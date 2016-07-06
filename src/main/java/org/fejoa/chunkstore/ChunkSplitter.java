/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.OutputStream;


abstract public class ChunkSplitter extends OutputStream {
    private boolean triggered = false;

    abstract protected boolean updateInternal(byte i);
    abstract protected void resetInternal();

    abstract public ChunkSplitter newInstance();

    public void reset() {
        triggered = false;
        resetInternal();
    }

    @Override
    public void write(int i) {
        if (updateInternal((byte)i))
            triggered = true;
    }

    public boolean update(byte i) {
        write(i);
        return isTriggered();
    }

    public boolean isTriggered() {
        return triggered;
    }
}
