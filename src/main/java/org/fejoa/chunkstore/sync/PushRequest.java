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
import java.util.List;


public class PushRequest {
    public enum Result {
        OK,
        PULL_REQUIRED
    }

    final private Repository repository;

    public PushRequest(Repository repository) {
        this.repository = repository;
    }

    private void getChunkContainerNodeChildChunks(ChunkContainerNode node, IChunkAccessor accessor,
                                                  List<HashValue> chunks) throws IOException, CryptoException {
        for (IChunkPointer chunkPointer : node.getChunkPointers()) {
            chunks.add(chunkPointer.getBoxPointer().getBoxHash());
            if (!ChunkContainerNode.isDataPointer(chunkPointer)) {
                ChunkContainerNode child = ChunkContainerNode.read(accessor, node, chunkPointer);
                getChunkContainerNodeChildChunks(child, accessor, chunks);
            }
        }
    }

    private List<HashValue> collectDiffs(IRepoChunkAccessors.ITransaction transaction,
                                         CommonAncestorsFinder.Chains chains)
            throws IOException, CryptoException {
        final List<HashValue> list = new ArrayList<>();
        for (CommonAncestorsFinder.SingleCommitChain chain : chains.chains)
            collectDiffs(transaction, chain, list);
        return list;
    }

    private void collectDiffs(IRepoChunkAccessors.ITransaction transaction, CommitBox parent, CommitBox child,
                              final List<HashValue> list) throws IOException, CryptoException {
        IChunkAccessor commitAccessor = transaction.getCommitAccessor();
        IChunkAccessor dirAccessor = transaction.getTreeAccessor();

        // add the child commit
        list.add(child.getBoxPointer().getBoxHash());
        // TODO: be more efficient and calculate the container diff
        ChunkContainer commitContainer = ChunkContainer.read(commitAccessor, child.getBoxPointer());
        getChunkContainerNodeChildChunks(commitContainer, commitAccessor, list);

        // diff of the commit trees
        DirectoryBox parentDir = null;
        if (parent != null)
            parentDir = DirectoryBox.read(dirAccessor, parent.getTree());
        DirectoryBox nextDir = DirectoryBox.read(dirAccessor, child.getTree());

        // add root dir
        list.add(child.getTree().getBoxHash());
        // TODO: be more efficient and calculate the container diff
        ChunkContainer rootDirContainer = ChunkContainer.read(commitAccessor, child.getTree());
        getChunkContainerNodeChildChunks(rootDirContainer, dirAccessor, list);

        DirBoxDiffIterator diffIterator = new DirBoxDiffIterator("", parentDir, nextDir);
        while (diffIterator.hasNext()) {
            DirBoxDiffIterator.Change<DirectoryBox.Entry> change = diffIterator.next();
            if (change.type == DiffIterator.Type.REMOVED)
                continue;
            // we are only interesting in modified and added changes
            IChunkAccessor changeAccessor = dirAccessor;
            if (change.theirs.isFile())
                changeAccessor =  transaction.getFileAccessor(change.path);

            BoxPointer theirsBoxPointer = change.theirs.getDataPointer();
            list.add(theirsBoxPointer.getBoxHash());
            // TODO: be more efficient and calculate the container diff
            ChunkContainer chunkContainer = ChunkContainer.read(changeAccessor, theirsBoxPointer);
            getChunkContainerNodeChildChunks(chunkContainer, changeAccessor, list);
        }
    }

    private void collectDiffs(IRepoChunkAccessors.ITransaction transaction,
                              CommonAncestorsFinder.SingleCommitChain chain,
                              final List<HashValue> list)
            throws IOException, CryptoException {
        for (int i = 0; i < chain.commits.size() - 1; i++) {
            CommitBox parent = chain.commits.get(i + 1);
            CommitBox child = chain.commits.get(i);
            if (list.contains(child.getBoxPointer()))
                continue;

            collectDiffs(transaction, parent, child, list);
        }
    }

    public Result push(IRemotePipe remotePipe, IRepoChunkAccessors.ITransaction transaction, String branch)
            throws IOException, CryptoException {
        IChunkAccessor commitAccessor = transaction.getCommitAccessor();
        ChunkStore.Transaction rawTransaction = transaction.getRawAccessor();

        CommitBox headCommit = repository.getHeadCommit();
        assert headCommit != null;

        CommonAncestorsFinder.Chains chainsToPush;
        ChunkStoreBranchLog.Entry logTip = LogEntryRequest.getRemoteTip(remotePipe, branch);
        if (logTip.getRev() > 0) { // remote has this branch
            BoxPointer remoteTip = repository.getCommitCallback().commitPointerFromLog(logTip.getMessage());
            if (!rawTransaction.contains(remoteTip.getBoxHash()))
                return Result.PULL_REQUIRED;


            CommitBox remoteCommit = CommitBox.read(commitAccessor, remoteTip);

            assert headCommit != null;
            chainsToPush = CommonAncestorsFinder.find(commitAccessor, remoteCommit, commitAccessor,
                    headCommit);

            boolean remoteCommitIsCommonAncestor = false;
            for (CommonAncestorsFinder.SingleCommitChain chain : chainsToPush.chains) {
                if (chain.getOldest().hash().equals(remoteCommit.hash())) {
                    remoteCommitIsCommonAncestor = true;
                    break;
                }
            }
            if (!remoteCommitIsCommonAncestor)
                return Result.PULL_REQUIRED;
        } else {
            chainsToPush = CommonAncestorsFinder.collectAllChains(commitAccessor, headCommit);
            // also push the first commit so add artificial null parents
            for (CommonAncestorsFinder.SingleCommitChain chain : chainsToPush.chains)
                chain.commits.add(chain.commits.size(), null);
        }

        List<HashValue> chunks = collectDiffs(transaction, chainsToPush);
        List<HashValue> remoteChunks = HasChunksRequest.hasChunks(remotePipe, chunks);
        for (HashValue chunk : remoteChunks)
            chunks.remove(chunk);

        // start request
        DataOutputStream outStream = new DataOutputStream(remotePipe.getOutputStream());
        Request.writeRequestHeader(outStream, Request.PUT_CHUNKS);
        StreamHelper.writeString(outStream, branch);
        outStream.writeInt(logTip.getRev());
        String logMessage = repository.getCommitCallback().commitPointerToLog(headCommit.getBoxPointer());
        StreamHelper.writeString(outStream, logMessage);
        outStream.writeInt(chunks.size());
        for (HashValue chunk : chunks) {
            outStream.write(chunk.getBytes());
            byte[] buffer = rawTransaction.getChunk(chunk);
            outStream.writeInt(buffer.length);
            outStream.write(buffer);
        }

        // read response
        DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
        Request.receiveHeader(inputStream, Request.PUT_CHUNKS);

        return Result.OK;
    }
}
