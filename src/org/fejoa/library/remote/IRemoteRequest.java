/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import rx.Observable;


public interface IRemoteRequest {
    public String getUrl();
    public Observable<byte[]> send(byte data[]);
}
