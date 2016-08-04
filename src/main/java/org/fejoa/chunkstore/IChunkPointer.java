/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public interface IChunkPointer {
    int getPointerLength();
    int getDataLength() throws IOException;
    BoxPointer getBoxPointer();
    void setBoxPointer(BoxPointer boxPointer);
    void read(DataInputStream inputStream) throws IOException;
    void write(DataOutputStream outputStream) throws IOException;
    IChunk getCachedChunk();
    void setCachedChunk(IChunk chunk);
    int getLevel();
    void setLevel(int level);
}
