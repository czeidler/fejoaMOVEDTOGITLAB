/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.fejoa.library.support.StreamHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;


class PackManager {
    private JGitInterface database;
    private Repository repository = null;

    public PackManager(JGitInterface gitInterface, Repository repository) {
        this.database = gitInterface;
        this.repository = repository;
    }

    public void importPack(byte data[], String base, String last, int format) throws IOException {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data));

        while (true) {
            String hash = readTill(stream, (byte)' ');
            if (hash.equals(""))
                break;
            String size = readTill(stream, (byte)0);
            int blobSize = Integer.parseInt(size);

            writeFile(hash, stream, blobSize);
        }

        String currentTip = database.getTip();
        String newTip = last;
        if (!currentTip.equals("") && !currentTip.equals(base))
            mergeBranches(base, currentTip, last, newTip);
        else
            database.updateTip(newTip);
    }

    private void writeFile(String hash, DataInputStream stream, int size) throws IOException {
        final String pathOrg = database.getPath();

        File dir = new File(pathOrg + "/objects/" + hash.substring(0, 2));
        dir.mkdir();
        File file = new File(dir, hash.substring(2));
        if (file.exists()) {
            stream.skipBytes(size);
            return;
        }

        FileOutputStream outputStream = new FileOutputStream(file);
        try {
            StreamHelper.copyBytes(stream, outputStream, size);
        } finally {
            outputStream.close();
        }
    }

    private String readTill(DataInputStream stream, byte stopChar)
    {
        String out = "";

        while (true) {
            byte data = 0;
            try {
                data = stream.readByte();
            } catch (IOException e) {
                return out;
            }
            if (data == stopChar)
                break;
            out += (char)data;
        }
        return out;
    }

    private void findMissingObjects(List<String> listOld, List<String> listNew, List<String> missing) {
        Collections.sort(listOld);
        Collections.sort(listNew);

        int a = 0;
        int b = 0;
        while (a < listOld.size() || b < listNew.size()) {
            int cmp;
            if (a < listOld.size() && b < listNew.size())
                cmp = listOld.get(a).compareTo(listNew.get(b));
            else
                cmp = 0;
            if (b >= listNew.size() || cmp < 0)
                a++;
            else if (a >= listOld.size() || cmp > 0) {
                missing.add(listNew.get(b));
                b++;
            } else {
                a++;
                b++;
            }
        }
    }

    private void collectAncestorCommits(String commit, List<String> ancestors) throws IOException {
        List<String> commits = new ArrayList<>();
        commits.add(commit);
        while (commits.size() > 0) {
            String currentCommit = commits.remove(0);
            if (ancestors.contains(currentCommit))
                continue;
            ancestors.add(currentCommit);

            // collect parents

            RevWalk walk = new RevWalk(repository);
            RevCommit revCommit = walk.parseCommit(ObjectId.fromString(currentCommit));

            for (int i = 0; i < revCommit.getParentCount(); i++) {
                RevCommit parent = revCommit.getParent(i);
                commits.add(parent.getId().name());
            }
        }
    }

    private void collectMissingBlobs(String commitStop, String commitLast, String ignoreCommit, List<String> blobs,
                                     int type) throws IOException {
        List<String> commits = new ArrayList<>();
        List<String> newObjects = new ArrayList<>();
        commits.add(commitLast);
        List<String> stopAncestorCommits = new ArrayList<>();
        if (ignoreCommit != "")
            stopAncestorCommits.add(ignoreCommit);
        boolean stopAncestorsCalculated = false;
        RevWalk walk = new RevWalk(repository);
        while (commits.size() > 0) {
            String currentCommit = commits.remove(0);
            if (currentCommit.equals(commitStop))
                continue;
            if (newObjects.contains(currentCommit))
                continue;
            newObjects.add(currentCommit);

            // collect tree objects
            RevCommit revCommit = walk.parseCommit(ObjectId.fromString(currentCommit));

            listTreeObjects(revCommit.getTree(), newObjects);

            // collect parents
            int parentCount = revCommit.getParentCount();
            if (parentCount > 1 && !stopAncestorsCalculated) {
                collectAncestorCommits(commitStop, stopAncestorCommits);
                stopAncestorsCalculated = true;
            }
            for (int i = 0; i < parentCount; i++) {
                RevCommit parent = revCommit.getParent(i);

                String parentString = parent.getId().name();
                // if we reached the ancestor commits tree we are done
                if (!stopAncestorCommits.contains(parentString))
                    commits.add(parentString);
            }
        }

        // get stop commit object tree
        List<String> stopCommitObjects = new ArrayList<>();
        if (commitStop != "") {
            RevCommit stopCommitObject = walk.parseCommit(ObjectId.fromString(commitStop));
            listTreeObjects(stopCommitObject.getTree(), stopCommitObjects);
        }

        // calculate the missing objects
        findMissingObjects(stopCommitObjects, newObjects, blobs);
    }

    private byte[] packObjects(List<String> objects) throws IOException {
        ByteArrayOutputStream packageData = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(packageData);

        for (int i = 0; i < objects.size(); i++) {
            ObjectLoader loader = repository.getObjectDatabase().newReader().open(ObjectId.fromString(objects.get(i)));

            // re-create the blob
            ByteArrayOutputStream blobCompressed = new ByteArrayOutputStream();
            DeflaterOutputStream blob = new DeflaterOutputStream(blobCompressed, new Deflater(), 8192);

            try {
                blob.write(Constants.encodedTypeString(loader.getType()));
                blob.write((byte)' ');
                blob.write(Constants.encodeASCII(loader.getSize()));
                blob.write((byte)0);
                blob.write(loader.getBytes(), 0, (int)loader.getSize());
            } finally {
                blob.close();
            }

            // pack the blob
            byte blobCompressedBytes[] = blobCompressed.toByteArray();
            out.writeBytes(objects.get(i));
            out.write((byte) ' ');
            out.write(Constants.encodeASCII(blobCompressedBytes.length));
            out.write((byte) 0);
            out.write(blobCompressedBytes, 0, blobCompressedBytes.length);
        }

        return packageData.toByteArray();
    }

    public byte[] exportPack(String commitOldest, String commitLatest, String ignoreCommit, int format) throws Exception {
        String commitEnd = new String(commitLatest);
        if (commitLatest.equals(""))
            commitEnd = database.getTip();
        if (commitEnd.equals(""))
            throw new Exception("no tip found");

        List<String> blobs = new ArrayList<>();
        collectMissingBlobs(commitOldest, commitEnd, ignoreCommit, blobs, -1);
        return packObjects(blobs);
    }

    private void listTreeObjects(RevTree tree, List<String> objects) throws IOException {

        String treeOidString = tree.getId().name();
        if (!objects.contains(treeOidString))
            objects.add(treeOidString);
        List<RevTree> treesQueue = new ArrayList<>();
        treesQueue.add(tree);

        boolean error = false;
        while (!error) {
            if (treesQueue.size() <= 0)
                break;
            RevTree currentTree = treesQueue.remove(0);
            TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.addTree(currentTree);
            while (treeWalk.next()) {
                String objectOidString = treeWalk.getObjectId(0).name();
                if (!objects.contains(objectOidString))
                    objects.add(objectOidString);
                if (treeWalk.isSubtree()) {
                    RevWalk walk = new RevWalk(repository);
                    RevTree revTree = walk.parseTree(treeWalk.getObjectId(0));
                    treesQueue.add(revTree);
                }
            }
        }
    }


    private void add(final int tree, final int stage, final NameConflictTreeWalk tw, final DirCacheBuilder builder,
                     final ObjectReader reader) throws IOException {
        final AbstractTreeIterator it = tw.getTree(tree, AbstractTreeIterator.class);
        if (it == null)
            return;

        if (FileMode.TREE.equals(tw.getRawMode(tree))) {
            builder.addTree(tw.getRawPath(), stage, reader, tw.getObjectId(tree));
        } else {
            final DirCacheEntry entry = new DirCacheEntry(tw.getRawPath(), stage);
            entry.setObjectIdFromRaw(it.idBuffer(), it.idOffset());
            entry.setFileMode(tw.getFileMode(tree));
            builder.add(entry);
        }
    }

    private boolean nonTree(final int mode) {
        return mode != 0 && !FileMode.TREE.equals(mode);
    }

    private void mergeBranches(String baseCommit, String ours, String theirs, String merge) throws IOException {
        final ObjectReader reader = repository.getObjectDatabase().newReader();

        RevCommit baseRevCommit = new RevWalk(repository).parseCommit(ObjectId.fromString(baseCommit));
        RevCommit oursCommit = new RevWalk(repository).parseCommit(ObjectId.fromString(ours));
        RevCommit theirsCommit = new RevWalk(repository).parseCommit(ObjectId.fromString(theirs));

        final NameConflictTreeWalk tw = new NameConflictTreeWalk(reader);
        tw.addTree(baseRevCommit.getTree());
        tw.addTree(oursCommit.getTree());
        tw.addTree(theirsCommit.getTree());

        final DirCache cache = DirCache.newInCore();
        final DirCacheBuilder builder = cache.builder();

        final int T_BASE = 0;
        final int T_OURS = 1;
        final int T_THEIRS = 2;

        while (tw.next()) {
            String test = tw.getPathString();
            final int modeO = tw.getRawMode(T_OURS);
            final int modeT = tw.getRawMode(T_THEIRS);
            if (modeO == modeT && tw.idEqual(T_OURS, T_THEIRS)) {
                add(T_OURS, DirCacheEntry.STAGE_0, tw, builder, reader);
                continue;
            }

            final int modeB = tw.getRawMode(T_BASE);
            if (modeB == modeO && tw.idEqual(T_BASE, T_OURS))
                add(T_THEIRS, DirCacheEntry.STAGE_0, tw, builder, reader);
            else if (modeB == modeT && tw.idEqual(T_BASE, T_THEIRS))
                add(T_OURS, DirCacheEntry.STAGE_0, tw, builder, reader);
            else {
                // just take ours for now:
                if (nonTree(modeO))
                    add(T_OURS, DirCacheEntry.STAGE_0, tw, builder, reader);

                /*
                if (nonTree(modeB)) {
                    add(T_BASE, DirCacheEntry.STAGE_1);
                    hasConflict = true;
                }
                if (nonTree(modeO)) {
                    add(T_OURS, DirCacheEntry.STAGE_2);
                    hasConflict = true;
                }
                if (nonTree(modeT)) {
                    add(T_THEIRS, DirCacheEntry.STAGE_3);
                    hasConflict = true;
                }*/

                if (tw.isSubtree())
                    tw.enterSubtree();
            }
        }
        builder.finish();

        ObjectInserter inserter = repository.newObjectInserter();
        ObjectId resultTree = cache.writeTree(inserter);
        inserter.flush();

        RevTree newRootTree = new RevWalk(repository).parseTree(resultTree);
        mergeCommit(newRootTree, baseRevCommit, oursCommit, theirsCommit);
    }

    private void mergeCommit(RevTree tree, RevCommit base, RevCommit parent1, RevCommit parent2)
            throws IOException {
        CommitBuilder commit = new CommitBuilder();
        PersonIdent personIdent = new PersonIdent("PackManager", "");
        commit.setCommitter(personIdent);
        commit.setAuthor(personIdent);
        commit.setMessage("merge");
        commit.setTreeId(tree);
        commit.addParentId(parent1);
        commit.addParentId(parent2);

        ObjectInserter objectInserter = repository.newObjectInserter();
        ObjectId commitId = objectInserter.insert(commit);
        objectInserter.flush();

        database.updateTip(commitId.name());
    }
}
