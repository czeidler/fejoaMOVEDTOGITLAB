/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.ContactPrivate;


public class ConnectionInfo {
    final public String server;
    final public String serverUser;
    final public ContactPrivate myself;

    public ConnectionInfo(String server, String serverUser, ContactPrivate myself) {
        this.server = server;
        this.serverUser = serverUser;
        this.myself = myself;
    }
}
