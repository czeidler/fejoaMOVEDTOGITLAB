/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;


public class RemoteRequestFactory {
    static public IRemoteRequest getRemoteRequest(String url) {
        return new HTMLRequest(url);
    }
}
