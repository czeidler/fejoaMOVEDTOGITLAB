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
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.IRemotePipe;

import java.io.*;


public class PullPushTest extends RepositoryTestBase {

    private ChunkStore createChunkStore(File directory, String name) throws IOException {
        assertTrue(!directory.getName().equals("") && !directory.getName().equals("."));
        cleanUpFiles.add(directory.getName());

        return ChunkStore.create(directory, name);
    }

    private IChunkAccessor getAccessor(final ChunkStore chunkStore) {
        return new IChunkAccessor() {
            @Override
            public DataInputStream getChunk(BoxPointer hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(chunkStore.getChunk(hash.getBoxHash())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data) throws IOException {
                return chunkStore.openTransaction().put(data);
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
                final IChunkAccessor accessor = getAccessor(chunkStore);
                return new RepoAccessorsTransactionBase(chunkStore) {
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

    public void testPull() throws IOException, CryptoException {
        String branch = "pullBranch";
        File directory = new File("PullTest");
        cleanUpFiles.add(directory.getName());
        directory.mkdirs();

        ChunkStore requestChunkStore = createChunkStore(directory, "requestStore");
        Repository requestRepo = new Repository(directory, branch, getRepoChunkAccessors(requestChunkStore),
                simpleCommitCallback);
        ChunkStore remoteChunkStore = createChunkStore(directory, "remoteStore");
        Repository remoteRepo = new Repository(directory, branch, getRepoChunkAccessors(remoteChunkStore),
                simpleCommitCallback);

        final PullHandler handler = new PullHandler(remoteChunkStore, remoteRepo.getBranchLog());
        final IRemotePipe senderPipe = connect(handler);

        PullRequest pullRequest = new PullRequest(requestChunkStore, requestRepo);
        BoxPointer pulledTip = pullRequest.pull(senderPipe);

        assertTrue(pulledTip.getBoxHash().isZero());

        remoteRepo.writeBytes("testFile", "Hello World".getBytes());
        BoxPointer boxPointer = remoteRepo.commit();

        pulledTip = pullRequest.pull(senderPipe);
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

        PushSender pushSender = new PushSender(senderRepo, new HashValue(HashValue.HASH_SIZE));
        pushSender.push(senderPipe);
    }*/
}

