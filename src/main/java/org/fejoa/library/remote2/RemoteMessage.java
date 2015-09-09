/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import java.io.InputStream;


public class RemoteMessage {
    final public String message;
    final public InputStream binaryData;

    public RemoteMessage(String message) {
        this.message = message;
        this.binaryData = null;
    }

    public RemoteMessage(String message, InputStream binaryData) {
        this.message = message;
        this.binaryData = binaryData;
    }
}
