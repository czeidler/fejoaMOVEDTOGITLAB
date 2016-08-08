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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


class TreeIterator implements Iterator<DiffIterator.Change<DirectoryBox.Entry>> {
    final private IChunkAccessor ourAccessor;
    final private IChunkAccessor theirAccessor;
    final private List<DirBoxDiffIterator> iterators = new ArrayList<>();
    private DirBoxDiffIterator current;

    public TreeIterator(IChunkAccessor ourAccessor, DirectoryBox ours, IChunkAccessor theirAccessor,
                        DirectoryBox theirs) {
        this.ourAccessor = ourAccessor;
        this.theirAccessor = theirAccessor;
        current = new DirBoxDiffIterator("", ours, theirs);
    }

    @Override
    public boolean hasNext() {
        return current.hasNext();
    }

    @Override
    public DiffIterator.Change<DirectoryBox.Entry> next() {
        DiffIterator.Change<DirectoryBox.Entry> next = current.next();
        if (next.type == DiffIterator.Type.MODIFIED && !next.ours.isFile() && !next.theirs.isFile()) {
            try {
                iterators.add(new DirBoxDiffIterator(next.path, DirectoryBox.read(ourAccessor, next.ours.getDataPointer()),
                        DirectoryBox.read(theirAccessor, next.theirs.getDataPointer())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (!hasNext() && iterators.size() > 0)
            current = iterators.remove(0);
        return next;
    }

    @Override
    public void remove() {

    }
}

public class ThreeWayMerge {
    public interface IConflictSolver {
        DirectoryBox.Entry solve(DirectoryBox.Entry ours, DirectoryBox.Entry theirs);
    }

    static public IConflictSolver ourSolver() {
        return new IConflictSolver() {
            @Override
            public DirectoryBox.Entry solve(DirectoryBox.Entry ours, DirectoryBox.Entry theirs) {
                return ours;
            }
        };
    }

    static public TreeAccessor merge(IRepoChunkAccessors.ITransaction outTransaction,
                                     IRepoChunkAccessors.ITransaction ourTransaction, CommitBox ours,
                                     IRepoChunkAccessors.ITransaction theirTransaction,
                                     CommitBox theirs, CommitBox parent, IConflictSolver conflictSolver)
            throws IOException, CryptoException {
        IChunkAccessor ourAccessor = ourTransaction.getTreeAccessor();
        IChunkAccessor theirAccessor = ourTransaction.getTreeAccessor();
        TreeIterator treeIterator = new TreeIterator(ourAccessor, DirectoryBox.read(ourAccessor, ours.getTree()),
                theirAccessor, DirectoryBox.read(theirAccessor, theirs.getTree()));

        DirectoryBox parentRoot = DirectoryBox.read(ourAccessor, parent.getTree());
        TreeAccessor parentTreeAccessor = new TreeAccessor(parentRoot, ourTransaction);
        TreeAccessor ourTreeAccessor = new TreeAccessor(DirectoryBox.read(ourAccessor, ours.getTree()), ourTransaction);
        TreeAccessor theirTreeAccessor = new TreeAccessor(DirectoryBox.read(theirAccessor, theirs.getTree()),
                theirTransaction);

        DirectoryBox ourRoot = DirectoryBox.read(outTransaction.getTreeAccessor(), ours.getTree());
        TreeAccessor outTree = new TreeAccessor(ourRoot, outTransaction);

        while (treeIterator.hasNext()) {
            DiffIterator.Change<DirectoryBox.Entry> change = treeIterator.next();
            if (change.type == DiffIterator.Type.ADDED) {
                DirectoryBox.Entry parentEntry = parentTreeAccessor.get(change.path);
                if (parentEntry == null) {
                    // add to ours
                    outTree.put(change.path, change.theirs);
                }
            } else if (change.type == DiffIterator.Type.REMOVED) {
                DirectoryBox.Entry parentEntry = parentTreeAccessor.get(change.path);
                if (parentEntry == null) {
                    // remove from ours
                    outTree.remove(change.path);
                }
            } else if (change.type == DiffIterator.Type.MODIFIED) {
                DirectoryBox.Entry ourEntry = ourTreeAccessor.get(change.path);
                if (!ourEntry.isFile())
                    continue;
                DirectoryBox.Entry theirEntry = theirTreeAccessor.get(change.path);
                outTree.put(change.path, conflictSolver.solve(ourEntry, theirEntry));
            }
        }

        return outTree;
    }
}
