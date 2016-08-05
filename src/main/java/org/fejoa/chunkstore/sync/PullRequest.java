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

    public GetCommitJob(Job parent, IRepoChunkAccessors.ITransaction transaction, BoxPointer pointer) {
        super(parent, transaction.getCommitAccessor(), pointer);
        this.transaction = transaction;
    }

    @Override
    public void onDone(ChunkFetcher chunkFetcher) throws IOException, CryptoException {
        if (doneCount > 0)
            return;
        doneCount++;

        CommitBox commitBox = CommitBox.read(chunkContainer);
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
    private List<Job> ongoingJobs = new ArrayList<>();

    public ChunkFetcher(ChunkStore chunkStore, IRemotePipe remotePipe) {
        this.chunkStore = chunkStore;
        this.remotePipe = remotePipe;
    }

    public void enqueueJob(Job job) {
        ongoingJobs.add(job);
    }

    public void fetch() throws IOException, CryptoException {
        ChunkStore.Transaction transaction = chunkStore.openTransaction();
        while (ongoingJobs.size() > 0) {
            List<Job> currentJobs = ongoingJobs;
            ongoingJobs = new ArrayList<>();

            ChunkRequest chunkRequest = new ChunkRequest(currentJobs);
            fetch(transaction, chunkRequest);
            for (Job job : currentJobs)
                job.onChunksFetched(this);
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
            inputStream.readFully(buffer);
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

    static public int receiveRequest(DataInputStream inputStream) throws IOException {
        int version = inputStream.readInt();
        if (version != PULL_REQUEST_VERSION)
            throw new IOException("Version " + PULL_REQUEST_VERSION + " expected but got:" + version);
        return inputStream.readInt();
    }

    static public void receiveHeader(DataInputStream inputStream, int request) throws IOException {
        int response = receiveRequest(inputStream);
        if (response != request)
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

        String remoteTipMessage = getRemoteTip(remotePipe);
        if (remoteTipMessage.equals(""))
            return new BoxPointer();
        BoxPointer remoteTip = requestRepo.getCommitCallback().commitPointerFromLog(remoteTipMessage);
        IRepoChunkAccessors.ITransaction transaction = requestRepo.getChunkAccessors().startTransaction();
        GetCommitJob getCommitJob = new GetCommitJob(null, transaction, remoteTip);
        chunkFetcher.enqueueJob(getCommitJob);
        chunkFetcher.fetch();

        //requestRepo.merge(getCommitJob);
        return remoteTip;
    }
}
