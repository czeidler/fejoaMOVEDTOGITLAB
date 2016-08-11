/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.BoxPointer;
import org.fejoa.chunkstore.CommitBox;
import org.fejoa.chunkstore.IChunkAccessor;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class CommonAncestorsFinder {
    // List string of following commits. If a commit has multiple parent only one parent is followed.
    static public class SingleCommitChain {
        public List<CommitBox> commits = new ArrayList<>();
        public boolean reachedFirstCommit = false;

        public SingleCommitChain(CommitBox head) {
            commits.add(head);
        }

        public SingleCommitChain clone() {
            SingleCommitChain clone = new SingleCommitChain();
            clone.commits.addAll(commits);
            clone.reachedFirstCommit = reachedFirstCommit;
            return clone;
        }

        private SingleCommitChain() {

        }

        public CommitBox getOldest() {
            assert commits.size() != 0;
            return commits.get(commits.size() - 1);
        }

        // make the chain terminate with the commonAncestor
        public void truncate(CommitBox commonAncestor) {
            int index = commits.indexOf(commonAncestor);
            while (commits.size() > index + 1)
                commits.remove(index + 1);
        }
    }

    static public class Chains {
        final public List<SingleCommitChain> chains = new ArrayList<>();

        protected void loadCommits(IChunkAccessor accessor, int numberOfCommits) throws IOException, CryptoException {
            for (SingleCommitChain chain : chains)
                CommonAncestorsFinder.loadCommits(accessor, chain, numberOfCommits, this);
        }

        public boolean allChainsFinished() {
            for (SingleCommitChain chain : chains) {
                if (!chain.reachedFirstCommit)
                    return false;
            }
            return true;
        }

        public SingleCommitChain getShortestChain() {
            int length = Integer.MAX_VALUE;
            SingleCommitChain shortestChain = null;
            for (SingleCommitChain chain : chains) {
                if (chain.commits.size() < length) {
                    length = chain.commits.size();
                    shortestChain = chain;
                }
            }
            return shortestChain;
        }
    }

    static private void loadCommits(IChunkAccessor accessor, SingleCommitChain commitChain,
                                    int numberOfCommits, Chains result) throws IOException, CryptoException {
        if (commitChain.reachedFirstCommit)
            return;
        CommitBox oldest = commitChain.getOldest();
        for (int i = 0; i < numberOfCommits; i++) {
            List<BoxPointer> parents = oldest.getParents();
            if (parents.size() == 0) {
                commitChain.reachedFirstCommit = true;
                return;
            }
            for (int p = 1; p < parents.size(); p++) {
                BoxPointer parent = parents.get(p);
                SingleCommitChain clone = commitChain.clone();
                CommitBox nextCommit = CommitBox.read(accessor, parent);
                clone.commits.add(nextCommit);
                result.chains.add(commitChain.clone());
                // follow this chain for a bit so that we stay on the same depth level
                loadCommits(accessor, clone, numberOfCommits - i - 1, result);
            }

            oldest = CommitBox.read(accessor, parents.get(0));
            commitChain.commits.add(oldest);
        }
    }

    static private CommitBox findCommonAncestorInOthers(SingleCommitChain localChain, SingleCommitChain otherChain) {
        //TODO: can be optimized by remembering which combinations we already checked, i.e. maintain a marker per chain
        for (CommitBox other : otherChain.commits) {
            for (CommitBox local : localChain.commits) {
                if (local.hash().equals(other.hash()))
                    return other;
            }
        }
        return null;
    }

    static public Chains collectAllChains(IChunkAccessor local, CommitBox localCommit)
            throws IOException, CryptoException {
        Chains chains = new Chains();
        SingleCommitChain startCommitChain = new SingleCommitChain(localCommit);
        chains.chains.add(startCommitChain);
        loadCommits(local, startCommitChain, Integer.MAX_VALUE, chains);
        return chains;
    }

    static public Chains find(IChunkAccessor local, CommitBox localCommit,
                              IChunkAccessor others, CommitBox othersCommit) throws IOException, CryptoException {
        assert localCommit != null;
        assert othersCommit != null;
        final int loadCommitsNumber = 3;

        Chains localChains = new Chains();
        localChains.chains.add(new SingleCommitChain(localCommit));
        Chains ongoingOthersChains = new Chains();
        ongoingOthersChains.chains.add(new SingleCommitChain(othersCommit));

        Chains results = new Chains();
        while (ongoingOthersChains.chains.size() > 0) {
            // check if all chains are finished
            if (localChains.allChainsFinished() && ongoingOthersChains.allChainsFinished())
                throw new IOException("No common ancestors.");

            localChains.loadCommits(local, loadCommitsNumber);
            ongoingOthersChains.loadCommits(others, loadCommitsNumber);

            for (SingleCommitChain localChain : localChains.chains) {
                Iterator<SingleCommitChain> iter = ongoingOthersChains.chains.iterator();
                while (iter.hasNext()) {
                    SingleCommitChain otherChain = iter.next();
                    CommitBox commonAncestor = findCommonAncestorInOthers(localChain, otherChain);
                    if (commonAncestor != null) {
                        iter.remove();
                        otherChain.truncate(commonAncestor);
                        results.chains.add(otherChain);
                    }
                }
            }
        }

        return results;
    }
}
