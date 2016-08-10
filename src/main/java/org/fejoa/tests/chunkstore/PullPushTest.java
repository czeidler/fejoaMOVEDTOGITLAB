/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.chunkstore;

import org.fejoa.chunkstore.*;
import org.fejoa.chunkstore.sync.*;
import org.fejoa.library.remote.IRemotePipe;
import org.fejoa.library.support.StorageLib;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class PullPushTest extends RepositoryTestBase {

    private ChunkStore createChunkStore(File directory, String name) throws IOException {
        assertTrue(!directory.getName().equals("") && !directory.getName().equals("."));
        cleanUpFiles.add(directory.getName());

        return ChunkStore.create(directory, name);
    }

    private IChunkAccessor getAccessor(final ChunkStore.Transaction transaction) {
        return new IChunkAccessor() {
            @Override
            public DataInputStream getChunk(BoxPointer hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(transaction.getChunk(hash.getBoxHash())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data) throws IOException {
                return transaction.put(data);
            }

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    private IRepoChunkAccessors getRepoChunkAccessors(final ChunkStore chunkStore) {
        return new IRepoChunkAccessors() {
            @Override
            public ITransaction startTransaction() throws IOException {
                return new RepoAccessorsTransactionBase(chunkStore) {
                    final IChunkAccessor accessor = getAccessor(transaction);
                    @Override
                    public ChunkStore.Transaction getRawAccessor() {
                        return transaction;
                    }

                    @Override
                    public IChunkAccessor getCommitAccessor() {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getTreeAccessor() {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getFileAccessor(String filePath) {
                        return accessor;
                    }
                };
            }
        };
    }

    private IRemotePipe connect(final IHandler handler) {
        return new IRemotePipe() {
            ByteArrayOutputStream outputStream;

            @Override
            public InputStream getInputStream() throws IOException {
                final ByteArrayOutputStream reply = new ByteArrayOutputStream();
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
                handler.handle(new IRemotePipe() {
                    @Override
                    public InputStream getInputStream() throws IOException {
                        return inputStream;
                    }

                    @Override
                    public OutputStream getOutputStream() {
                        return reply;
                    }
                });
                return new ByteArrayInputStream(reply.toByteArray());
            }

            @Override
            public OutputStream getOutputStream() {
                outputStream = new ByteArrayOutputStream();
                return outputStream;
            }
        };
    }

    public void testPull() throws Exception {
        String branch = "pullBranch";
        File directory = new File("PullTest");
        cleanUpFiles.add(directory.getName());
        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
        directory.mkdirs();

        ChunkStore requestChunkStore = createChunkStore(directory, "requestStore");
        Repository requestRepo = new Repository(directory, branch, getRepoChunkAccessors(requestChunkStore),
                simpleCommitCallback);
        ChunkStore remoteChunkStore = createChunkStore(directory, "remoteStore");
        final Repository remoteRepo = new Repository(directory, branch, getRepoChunkAccessors(remoteChunkStore),
                simpleCommitCallback);

        final RequestHandler handler = new RequestHandler(remoteChunkStore, new RequestHandler.IBranchLogGetter() {
            @Override
            public ChunkStoreBranchLog get(String branch) throws IOException {
                return remoteRepo.getBranchLog();
            }
        });
        final IRemotePipe senderPipe = connect(handler);

        PullRequest pullRequest = new PullRequest(requestChunkStore, requestRepo);
        BoxPointer pulledTip = pullRequest.pull(senderPipe, branch);

        assertTrue(pulledTip.getBoxHash().isZero());

        // change the remote repo
        List<DatabaseStingEntry> remoteContent = new ArrayList<>();
        add(remoteRepo, remoteContent, new DatabaseStingEntry("testFile", "Hello World"));
        BoxPointer boxPointer = remoteRepo.commit();

        pulledTip = pullRequest.pull(senderPipe, branch);
        containsContent(requestRepo, remoteContent);
        assertTrue(pulledTip.getBoxHash().equals(boxPointer.getBoxHash()));

        // make another remote change
        add(remoteRepo, remoteContent, new DatabaseStingEntry("testFile2", "Hello World 2"));
        boxPointer = remoteRepo.commit();

        pulledTip = pullRequest.pull(senderPipe, branch);
        containsContent(requestRepo, remoteContent);
        assertTrue(pulledTip.getBoxHash().equals(boxPointer.getBoxHash()));
    }
/*
    public void testPush() throws IOException, CryptoException {
        String branch = "pushBranch";
        File directory = new File("PushTest");
        cleanUpFiles.add(directory.getName());
        directory.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, "senderStore");
        IRepoChunkAccessors accessors = getRepoChunkAccessors(chunkStore);
        Repository senderRepo = new Repository(directory, branch, accessors);
        Repository receiverRepo = new Repository(directory, branch,
                getRepoChunkAccessors(createChunkStore(directory, "receiverStore")));


        final PushHandler handler = new PushHandler(receiverRepo.getBranchLog());

        final IRemotePipe senderPipe = connect(handler);

        PushRequest pushSender = new PushRequest(senderRepo, new HashValue(HashValue.HASH_SIZE));
        pushSender.push(senderPipe);
    }*/
}

