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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.fejoa.chunkstore.sync.PullRequest.GET_CHUNKS;


abstract class Job {
    final private Job parent;
    final private List<Job> childJobs = new ArrayList<>();

    public Job(Job parent) {
        this.parent = parent;
        if (parent != null)
            parent.childJobs.add(this);
    }

    abstract public Collection<HashValue> getRequestedChunks();
    abstract protected void enqueueJobsAfterChunksFetched(ChunkFetcher chunkFetcher) throws IOException, CryptoException;

    final public void onChunksFetched(ChunkFetcher chunkFetcher) throws IOException, CryptoException {
        enqueueJobsAfterChunksFetched(chunkFetcher);
        checkIfDone(chunkFetcher);
    }

    public void onDone(ChunkFetcher chunkFetcher) throws IOException, CryptoException {

    }

    final private void onChildDone(Job job, ChunkFetcher chunkFetcher) throws IOException, CryptoException {
        boolean removed = childJobs.remove(job);
        assert removed;
        checkIfDone(chunkFetcher);
    }

    private void checkIfDone(ChunkFetcher chunkFetcher) throws IOException, CryptoException {
        if (childJobs.size() == 0) {
            onDone(chunkFetcher);
            // have new jobs been added?
            if (parent != null && childJobs.size() == 0)
                parent.onChildDone(this, chunkFetcher);
        }
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
    final private ChunkContainerNode node;

    public GetChunkContainerNodeJob(Job parent, IChunkAccessor accessor, ChunkContainerNode node) {
        super(parent);

        this.accessor = accessor;
        this.node = node;
    }

    @Override
    public Collection<HashValue> getRequestedChunks() {
        List<HashValue> children = new ArrayList<>();
        for (IChunkPointer pointer : node.getChunkPointers())
            children.add(pointer.getBoxPointer().getBoxHash());
        return children;
    }

    @Override
    protected void enqueueJobsAfterChunksFetched(ChunkFetcher chunkFetcher) throws IOException, CryptoException {
        if (node.isLeafNode())
            return;
        for (IChunkPointer child : node.getChunkPointers()) {
            ChunkContainerNode childNode = ChunkContainerNode.read(accessor, node, child);
            chunkFetcher.enqueueJob(new GetChunkContainerNodeJob(this, accessor, childNode));
        }
    }
}

class GetChunkContainerJob extends RootObjectJob {
    protected ChunkContainer chunkContainer;
    public GetChunkContainerJob(Job parent, IChunkAccessor accessor, BoxPointer pointer) {
        super(parent, accessor, pointer);
    }

    @Override
    protected void enqueueJobsAfterChunksFetched(ChunkFetcher chunkFetcher) throws IOException, CryptoException {
        chunkContainer = ChunkContainer.read(accessor, boxPointer);
        chunkFetcher.enqueueJob(new GetChunkContainerNodeJob(this, accessor, chunkContainer));
    }
}

class GetCommitJob extends GetChunkContainerJob {
    private int doneCount = 0;
    private IRepoChunkAccessors.ITransaction transaction;
    private CommitBox commitBox;

    public GetCommitJob(Job parent, IRepoChunkAccessors.ITransaction transaction, BoxPointer pointer) {
        super(parent, transaction.getCommitAccessor(), pointer);
        this.transaction = transaction;
    }

    public CommitBox getCommitBox() {
        return commitBox;
    }

    @Override
    public void onDone(ChunkFetcher chunkFetcher) throws IOException, CryptoException {
        if (doneCount > 0)
            return;
        doneCount++;

        commitBox = CommitBox.read(chunkContainer);
        System.out.println(commitBox);
        for (BoxPointer parent : commitBox.getParents())
            chunkFetcher.enqueueJob(new GetCommitJob(this, transaction, parent));
        chunkFetcher.enqueueJob(new GetDirJob(this, transaction, commitBox.getTree(), ""));
    }
}

class GetDirJob extends GetChunkContainerJob {
    private int doneCount = 0;
    final private IRepoChunkAccessors.ITransaction transaction;
    final private String path;

    public GetDirJob(Job parent, IRepoChunkAccessors.ITransaction transaction, BoxPointer pointer, String path) {
        super(parent, transaction.getTreeAccessor(), pointer);

        this.transaction = transaction;
        this.path = path;
    }

    @Override
    public void onDone(ChunkFetcher chunkFetcher) throws IOException, CryptoException {
        if (doneCount > 0)
            return;
        doneCount++;
        DirectoryBox directoryBox = DirectoryBox.read(chunkContainer);
        System.out.println(directoryBox);

        for (DirectoryBox.Entry entry : directoryBox.getEntries()) {
            if (entry.isFile()) {
                chunkFetcher.enqueueJob(new GetChunkContainerJob(this,
                        transaction.getFileAccessor(path + "/" + entry.getName()), entry.getDataPointer()));
            } else {
                chunkFetcher.enqueueJob(new GetDirJob(this, transaction, entry.getDataPointer(),
                        path + "/" + entry.getName()));
            }
        }
    }
}


public class ChunkFetcher {
    interface IFetcherBackend {
        void fetch(ChunkStore.Transaction transaction, List<HashValue> requestedChunks) throws IOException;
    }

    class ChunkRequest {
        final private List<HashValue> requestedChunks = new ArrayList<>();

        public ChunkRequest(List<Job> jobs) {
            for (Job job : jobs)
                requestedChunks.addAll(job.getRequestedChunks());
        }
    }

    final private ChunkStore.Transaction transaction;
    final private IFetcherBackend fetcherBackend;
    private List<Job> ongoingJobs = new ArrayList<>();

    static public ChunkFetcher createLocalFetcher(final ChunkStore.Transaction target,
                                                  final ChunkStore.Transaction source) {
        return new ChunkFetcher(target, new IFetcherBackend() {
            @Override
            public void fetch(ChunkStore.Transaction target, List<HashValue> requestedChunks) throws IOException {
                for (HashValue requestedChunk : requestedChunks) {
                    byte[] buffer = source.getChunk(requestedChunk);
                    if (buffer == null)
                        throw new IOException("Requested chunk not found.");
                    PutResult<HashValue> result = target.put(buffer);
                    if (!result.key.equals(requestedChunk))
                        throw new IOException("Hash miss match.");
                }
            }
        });
    }

    public ChunkFetcher(ChunkStore.Transaction transaction, IFetcherBackend fetcherBackend) {
        this.transaction = transaction;
        this.fetcherBackend = fetcherBackend;
    }

    public void enqueueGetCommitJob(IRepoChunkAccessors.ITransaction transaction, BoxPointer commitPointer) {
        enqueueJob(new GetCommitJob(null, transaction, commitPointer));
    }

    public void enqueueJob(Job job) {
        ongoingJobs.add(job);
    }

    public void fetch() throws IOException, CryptoException {
        while (ongoingJobs.size() > 0) {
            List<Job> currentJobs = ongoingJobs;
            ongoingJobs = new ArrayList<>();

            ChunkRequest chunkRequest = new ChunkRequest(currentJobs);
            fetcherBackend.fetch(transaction, chunkRequest.requestedChunks);
            for (Job job : currentJobs)
                job.onChunksFetched(this);
        }
        transaction.commit();
    }
}
