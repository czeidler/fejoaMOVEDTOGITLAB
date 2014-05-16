package org.fejoa.library.git;


import java.io.IOException;

public interface IDatabaseInterface {
    public boolean init(String path, String branch, boolean create);

    public String getBranch();

    public String readString(String path) throws IOException ;
    public byte[] readBytes(String path) throws IOException ;

    public void writeBytes(String path, byte[] bytes) throws Exception;

    public void commit() throws Exception;
}
