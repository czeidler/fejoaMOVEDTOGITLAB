/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.IRemotePipe;
import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.fejoa.chunkstore.sync.PullRequest.GET_CHUNKS;
import static org.fejoa.chunkstore.sync.PullRequest.receiveHeader;


abstract class Job {
    final private Job parent;
    private List<Job> childJobs;

    public Job(Job parent) {
        this.parent = parent;
    }

    abstract public Collection<HashValue> getRequestedChunks();
    abstract protected List<Job> getChildJobsAfterChunksFetched() throws IOException, CryptoException;

    final public List<Job> onChunksFetched() throws IOException, CryptoException {
        childJobs = getChildJobsAfterChunksFetched();
        if (childJobs.size() == 0)
            notifyParentWeAreDone();
        return childJobs;
    }

    final private void onChildDone(Job job) {
        assert childJobs.remove(job);
        if (childJobs.size() == 0)
            notifyParentWeAreDone();
    }

    final private void notifyParentWeAreDone() {
        if (parent != null)
            parent.onChildDone(this);
    }
}

abstract class RootObjectJob extends Job {
    final protected IChunkAccessor accessor;
    final protected BoxPointer boxPointer;

    public RootObjectJob(Job parent, IChunkAccessor accessor, BoxPointer pointer) {
        super(parent);

        this.accessor = accessor;
        this.boxPointer = pointer;
    }

    @Override
    public Collection<HashValue> getRequestedChunks() {
        return Collections.singleton(boxPointer.getBoxHash());
    }
}

class GetChunkContainerNodeJob extends Job {
    final private IChunkAccessor accessor;
    final private ChunkContainerNode parentNode;

    public GetChunkContainerNodeJob(Job parent, IChunkAccessor accessor, ChunkContainerNode parentNode) {
        super(parent);

        this.accessor = accessor;
        this.parentNode = parentNode;
    }

    @Override
    public Collection<HashValue> getRequestedChunks() {
        List<HashValue> children = new ArrayList<>();
        for (IChunkPointer pointer : parentNode.getChunkPointers())
            children.add(pointer.getBoxPointer().getBoxHash());
        return children;
    }

    @Override
    protected List<Job> getChildJobsAfterChunksFetched() throws IOException, CryptoException {
        if (parentNode.isDataNode())
            return Collections.emptyList();
        List<Job> childJobs = new ArrayList<>();
        for (IChunkPointer child : parentNode.getChunkPointers()) {
            ChunkContainerNode childNode = ChunkContainerNode.read(accessor, parentNode, child);
            childJobs.add(new GetChunkContainerNodeJob(this, accessor, childNode));
        }
        return childJobs;
    }
}

class GetChunkContainerJob extends RootObjectJob {
    public GetChunkContainerJob(Job parent, IChunkAccessor accessor, BoxPointer pointer) {
        super(parent, accessor, pointer);
    }

    @Override
    protected List<Job> getChildJobsAfterChunksFetched() throws IOException, CryptoException {
        ChunkContainer chunkContainer = new ChunkContainer(accessor, boxPointer);
        return Collections.singletonList((Job)(new GetChunkContainerNodeJob(this, accessor, chunkContainer)));
    }
}

class GetCommitJob extends RootObjectJob {
    public GetCommitJob(Job parent, IChunkAccessor accessor, BoxPointer pointer) {
        super(parent, accessor, pointer);
    }

    @Override
    protected List<Job> getChildJobsAfterChunksFetched() throws IOException, CryptoException {
        return Collections.singletonList((Job)(new GetChunkContainerJob(this, accessor, boxPointer)));
    }
}

class ChunkFetcher {
    class ChunkRequest {
        final private List<HashValue> requestedChunks = new ArrayList<>();
        final private List<Job> jobs;

        public ChunkRequest(List<Job> jobs) {
            this.jobs = jobs;
            for (Job job : jobs)
                requestedChunks.addAll(job.getRequestedChunks());
        }
    }

    final private ChunkStore chunkStore;
    final private IRemotePipe remotePipe;

    public ChunkFetcher(ChunkStore chunkStore, IRemotePipe remotePipe) {
        this.chunkStore = chunkStore;
        this.remotePipe = remotePipe;
    }

    public void fetch(List<Job> jobs) throws IOException, CryptoException {
        List<Job> currentJobs = jobs;
        ChunkStore.Transaction transaction = chunkStore.openTransaction();
        while (jobs.size() > 0) {
            ChunkRequest chunkRequest = new ChunkRequest(jobs);
            fetch(transaction, chunkRequest);
            List<Job> childJobs = new ArrayList<>();
            for (Job job : currentJobs)
                childJobs.addAll(job.onChunksFetched());
            currentJobs = childJobs;
        }
        transaction.commit();
    }

    private void fetch(ChunkStore.Transaction transaction, ChunkRequest chunkRequest) throws IOException {
        DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
        PullRequest.writeRequestHeader(outputStream, GET_CHUNKS);
        outputStream.writeInt(chunkRequest.requestedChunks.size());
        for (HashValue hashValue : chunkRequest.requestedChunks)
            outputStream.write(hashValue.getBytes());

        DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
        PullRequest.receiveHeader(inputStream, GET_CHUNKS);
        int chunkCount = inputStream.readInt();
        if (chunkCount != chunkRequest.requestedChunks.size()) {
            throw new IOException("Received chunk count is: " + chunkCount + " but "
                    + chunkRequest.requestedChunks.size() + " expected.");
        }

        for (int i = 0; i < chunkCount; i++) {
            HashValue hashValue = new HashValue(HashValue.HASH_SIZE);
            inputStream.readFully(hashValue.getBytes());
            int size = inputStream.readInt();
            byte[] buffer = new byte[size];
            PutResult<HashValue> result = transaction.put(buffer);
            if (!result.key.equals(hashValue))
                throw new IOException("Hash miss match.");
        }
    }
}

public class PullRequest {
    static final public int PULL_REQUEST_VERSION = 1;
    static final public int GET_REMOTE_TIP = 1;
    static final public int GET_CHUNKS = 2;

    final private ChunkStore chunkStore;
    final private Repository requestRepo;

    public PullRequest(ChunkStore chunkStore, Repository requestRepo) {
        this.chunkStore = chunkStore;
        this.requestRepo = requestRepo;
    }

    static public void writeRequestHeader(DataOutputStream outputStream, int request) throws IOException {
        outputStream.writeInt(PULL_REQUEST_VERSION);
        outputStream.writeInt(request);
    }

    static public void receiveHeader(DataInputStream inputStream, int request) throws IOException {
        int version = inputStream.readInt();
        if (version != PULL_REQUEST_VERSION)
            throw new IOException("Version " + PULL_REQUEST_VERSION + " expected but got:" + version);
        int response = inputStream.readInt();
        if (response != GET_REMOTE_TIP)
            throw new IOException("GET_REMOTE_TIP response expected but got: " + response);
    }

    private String getRemoteTip(IRemotePipe remotePipe) throws IOException {
        DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
        writeRequestHeader(outputStream, GET_REMOTE_TIP);
        DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
        receiveHeader(inputStream, GET_REMOTE_TIP);

        String remoteTipString = StreamHelper.readString(inputStream);

        return remoteTipString;
    }

    public BoxPointer pull(IRemotePipe remotePipe) throws IOException, CryptoException {
        ChunkFetcher chunkFetcher = new ChunkFetcher(chunkStore, remotePipe);

        BoxPointer remoteTip = requestRepo.getCommitCallback().commitPointerFromLog(getRemoteTip(remotePipe));
        IRepoChunkAccessors.ITransaction transaction = requestRepo.getChunkAccessors().startTransaction();
        GetCommitJob getCommitJob = new GetCommitJob(null, transaction.getCommitAccessor(), remoteTip);
        chunkFetcher.fetch(Collections.singletonList((Job)getCommitJob));

        //requestRepo.merge(getCommitJob);
        return remoteTip;
    }
}
