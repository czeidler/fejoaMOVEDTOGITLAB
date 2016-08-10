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
    final private HashValue remoteTip;

    public PushRequest(Repository repository, HashValue remoteTip) {
        this.repository = repository;
        this.remoteTip = remoteTip;
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
                                         CommonAncestorsFinder.Chains chains) throws IOException, CryptoException {
        final List<HashValue> list = new ArrayList<>();
        for (CommonAncestorsFinder.SingleCommitChain chain : chains.chains)
            collectDiffs(transaction, chain, list);
        return list;
    }

    private void collectDiffs(IRepoChunkAccessors.ITransaction transaction,
                              CommonAncestorsFinder.SingleCommitChain chain,
                              final List<HashValue> list) throws IOException, CryptoException {
        IChunkAccessor dirAccessor = transaction.getTreeAccessor();
        CommitBox parent = chain.getOldest();
        for (int i = chain.commits.size() - 2; i >= 0; i--) {
            CommitBox next = chain.commits.get(i);
            DirectoryBox parentDir = DirectoryBox.read(dirAccessor, parent.getTree());
            DirectoryBox nextDir = DirectoryBox.read(dirAccessor, next.getTree());
            DirBoxDiffIterator diffIterator = new DirBoxDiffIterator("", parentDir, nextDir);
            while (diffIterator.hasNext()) {
                DirBoxDiffIterator.Change<DirectoryBox.Entry> change = diffIterator.next();
                if (change.type == DiffIterator.Type.REMOVED)
                    continue;
                // we are only interesting in modified and added changes
                BoxPointer theirsBoxPointer = change.theirs.getDataPointer();
                list.add(theirsBoxPointer.getBoxHash());

                IChunkAccessor changeAccesor = dirAccessor;
                if (change.theirs.isFile())
                    changeAccesor =  transaction.getFileAccessor(change.path);

                // TODO: be more efficient and calculate the container diff
                ChunkContainer chunkContainer = ChunkContainer.read(changeAccesor, theirsBoxPointer);
                getChunkContainerNodeChildChunks(chunkContainer, changeAccesor, list);
            }
        }
    }

    public Result push(IRemotePipe remotePipe, IRepoChunkAccessors.ITransaction transaction, String branch)
            throws IOException, CryptoException {
        ChunkStoreBranchLog.Entry logTip = LogEntryRequest.getRemoteTip(remotePipe, branch);
        BoxPointer remoteTip = repository.getCommitCallback().commitPointerFromLog(logTip.getMessage());
        ChunkStore.Transaction rawTransaction = transaction.getRawAccessor();
        if (!rawTransaction.contains(remoteTip.getBoxHash()))
            return Result.PULL_REQUIRED;

        IChunkAccessor commitAccessor = transaction.getCommitAccessor();
        CommitBox remoteCommit = CommitBox.read(commitAccessor, remoteTip);
        CommitBox headCommit = repository.getHeadCommit();
        assert headCommit != null;
        CommonAncestorsFinder.Chains chains = CommonAncestorsFinder.find(commitAccessor, remoteCommit, commitAccessor,
                headCommit);

        boolean remoteCommitIsCommonAncestor = false;
        for (CommonAncestorsFinder.SingleCommitChain chain : chains.chains) {
            if (chain.getOldest().hash().equals(remoteCommit.hash())) {
                remoteCommitIsCommonAncestor = true;
                break;
            }
        }
        if (!remoteCommitIsCommonAncestor)
            return Result.PULL_REQUIRED;

        List<HashValue> chunks = collectDiffs(transaction, chains);
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
        for (HashValue chunk : chunks)
            outStream.write(chunk.getBytes());

        return Result.OK;
    }
}
