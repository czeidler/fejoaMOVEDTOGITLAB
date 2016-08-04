/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public interface IRemotePipe {
    InputStream getInputStream() throws IOException;
    OutputStream getOutputStream();
}
