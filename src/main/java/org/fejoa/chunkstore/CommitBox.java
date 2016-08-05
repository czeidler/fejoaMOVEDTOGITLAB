/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CommitBox extends TypedBlob {
    private BoxPointer that;
    private BoxPointer tree;
    final private List<BoxPointer> parents = new ArrayList<>();
    final private Map<BoxPointer, CommitBox> parentCache = new HashMap<>();
    private byte[] commitMessage;

    private CommitBox() {
        super(BlobTypes.COMMIT);
    }

    static public CommitBox create() {
        return new CommitBox();
    }

    static public CommitBox read(IChunkAccessor accessor, BoxPointer pointer)
            throws IOException, CryptoException {
        ChunkContainer chunkContainer = ChunkContainer.read(accessor, pointer);
        return read(chunkContainer);
    }

    static public CommitBox read(ChunkContainer chunkContainer)
            throws IOException, CryptoException {
        return read(BlobTypes.COMMIT, new DataInputStream(new ChunkContainerInputStream(chunkContainer)),
                chunkContainer.getBoxPointer());
    }

    static private CommitBox read(short type, DataInputStream inputStream, BoxPointer that) throws IOException {
        assert type == BlobTypes.COMMIT;
        CommitBox commitBox = new CommitBox();
        commitBox.that = that;
        commitBox.read(inputStream);
        return commitBox;
    }

    public void setTree(BoxPointer tree) {
        this.tree = tree;
    }

    public BoxPointer getTree() {
        return tree;
    }

    public void setCommitMessage(byte[] commitMessage) {
        this.commitMessage = commitMessage;
    }

    public byte[] getCommitMessage() {
        return commitMessage;
    }

    public void addParent(BoxPointer parent) {
        parents.add(parent);
    }

    public CommitBox getParent(IChunkAccessor accessor, int i) throws IOException, CryptoException {
        BoxPointer pointer = parents.get(i);
        CommitBox parent = parentCache.get(pointer);
        if (parent != null)
            return parent;
        parent = CommitBox.read(accessor, pointer);
        parentCache.put(pointer, parent);
        return parent;
    }

    public List<BoxPointer> getParents() {
        return parents;
    }

    public HashValue hash() {
        try {
            MessageDigest messageDigest = CryptoHelper.sha256Hash();
            messageDigest.reset();
            messageDigest.update(tree.getDataHash().getBytes());
            for (BoxPointer parent : parents)
                messageDigest.update(parent.getDataHash().getBytes());
            messageDigest.update(commitMessage);
            return new HashValue(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void readInternal(DataInputStream inputStream) throws IOException {
        this.setTree(new BoxPointer());
        this.getTree().read(inputStream);

        int commitMessageSize = inputStream.readInt();
        this.commitMessage = new byte[commitMessageSize];
        inputStream.readFully(commitMessage);

        short numberOfParents = inputStream.readShort();
        for (int i = 0; i < numberOfParents; i++) {
            BoxPointer parent = new BoxPointer();
            parent.read(inputStream);
            addParent(parent);
        }
    }

    @Override
    protected void writeInternal(DataOutputStream outputStream) throws IOException {
        getTree().write(outputStream);

        outputStream.writeInt(commitMessage.length);
        outputStream.write(commitMessage);

        List<BoxPointer> parents = getParents();
        outputStream.writeShort(parents.size());
        for (BoxPointer parent : parents)
            parent.write(outputStream);
    }

    @Override
    public String toString() {
        if (tree == null || commitMessage == null)
            return "invalid";
        String out = "Tree: " + tree + "\n";
        out += "Commit data: " + new String(commitMessage) + "\n";
        out += "" + parents.size() + " parents: ";
        for (int i = 0; i < parents.size(); i++)
            out += "\n\t" + parents.get(i);
        return out;
    }

    public void setBoxPointer(BoxPointer boxPointer) {
        this.that = boxPointer;
    }

    public BoxPointer getBoxPointer() {
        return that;
    }
}
