package org.fejoa.library.database;


import java.io.IOException;
import java.util.List;

public interface IDatabaseInterface {
    public void init(String path, String branch, boolean create) throws IOException;

    public String getPath();
    public String getBranch();

    public byte[] readBytes(String path) throws IOException;
    public void writeBytes(String path, byte[] bytes) throws Exception;

    public void commit() throws Exception;

    public List<String> listFiles(String path) throws IOException;
    public List<String> listDirectories(String path) throws IOException;
}
