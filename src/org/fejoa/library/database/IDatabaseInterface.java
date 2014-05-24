package org.fejoa.library.database;


import java.io.IOException;

public interface IDatabaseInterface {
    public void init(String path, String branch, boolean create) throws IOException;

    public String getBranch();

    public byte[] readBytes(String path) throws IOException;
    public void writeBytes(String path, byte[] bytes) throws Exception;

    public void commit() throws Exception;
}
