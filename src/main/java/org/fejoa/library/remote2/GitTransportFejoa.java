/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;


public class GitTransportFejoa extends Transport implements WalkTransport,
        PackTransport {

    class SmartHttpFetchConnection extends BasePackFetchConnection {

        SmartHttpFetchConnection()
                throws TransportException {
            super(GitTransportFejoa.this);
            statelessRPC = true;
        }

        public void readAdvertisedRefs(InputStream advertisement) throws TransportException {
            init(advertisement, DisabledOutputStream.INSTANCE);
            outNeedsEnd = false;
            readAdvertisedRefs();
        }

        @Override
        protected void doFetch(final ProgressMonitor monitor,
                               final Collection<Ref> want, final Set<ObjectId> have) throws TransportException {
            RemotePipe pipe = new RemotePipe(pushHeader, remoteRequest, null);
            init(pipe.getInputStream(), pipe.getOutputStream());
            super.doFetch(monitor, want, have);
        }

        @Override
        protected void onReceivePack() {
        }
    }

    class SmartFejoaPushConnection extends BasePackPushConnection {
        SmartFejoaPushConnection()
                throws TransportException {
            super(GitTransportFejoa.this);
            statelessRPC = true;
        }

        public void readAdvertisedRefs(InputStream advertisement) throws TransportException {
            init(advertisement, DisabledOutputStream.INSTANCE);
            outNeedsEnd = false;
            readAdvertisedRefs();
        }

        @Override
        protected void doPush(final ProgressMonitor monitor,
                              final Map<String, RemoteRefUpdate> refUpdates) throws TransportException {
            RemotePipe pipe = new RemotePipe(pushHeader, remoteRequest, null);
            init(pipe.getInputStream(), pipe.getOutputStream());
            super.doPush(monitor, refUpdates);
        }
    }

    final private IRemoteRequest remoteRequest;
    private boolean useSmartHttp = true;
    final private String pushHeader;

    protected GitTransportFejoa(Repository local, IRemoteRequest remoteRequest, String pushHeader) {
        super(local, null);

        this.remoteRequest = remoteRequest;
        this.pushHeader = pushHeader;
    }

    @Override
    public FetchConnection openFetch() throws NotSupportedException, TransportException {
        return new SmartHttpFetchConnection();
    }

    @Override
    public PushConnection openPush() throws NotSupportedException, TransportException {
        try {
            if (useSmartHttp) {
                return new SmartFejoaPushConnection();
            } else {
                final String msg = JGitText.get().remoteDoesNotSupportSmartHTTPPush;
                throw new NotSupportedException(msg);
            }
        } catch (NotSupportedException err) {
            throw err;
        }  catch (IOException err) {
            throw new TransportException(uri, JGitText.get().errorReadingInfoRefs, err);
        }
    }

    @Override
    public void close() {

    }
}
