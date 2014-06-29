/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;


public class RemoteStorage {
    private String uid;
    private IRemoteRequest remoteRequest;
    private IAuthenticationRequest authenticationRequest;

    public String getUid() {
        return uid;
    }

    public IRemoteRequest getRemoteRequest() {
        return remoteRequest;
    }

    public IAuthenticationRequest getAuthenticationRequest() {
        return authenticationRequest;
    }



}
