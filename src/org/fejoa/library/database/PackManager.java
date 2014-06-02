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
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.StrategySimpleTwoWayInCore;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;


class PackManager {
    private JGitInterface database;
    private Repository repository = null;

    public PackManager(JGitInterface gitInterface, Repository repository) {
        this.database = gitInterface;
        this.repository = repository;
    }

    public void importPack(byte data[], String base, String last) throws IOException {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data));

        int objectStart = 0;
        while (objectStart < data.length) {
            int objectEnd = objectStart;
            String hash = readTill(stream, ' ');
            String size = readTill(stream, '\0');
            int blobStart = objectEnd;
            objectEnd += Integer.parseInt(size);

            writeFile(hash, data, blobStart, objectEnd - blobStart);

            objectStart = objectEnd;
        }

        String currentTip = database.getTip();
        String newTip = last;
        if (currentTip != base)
            mergeBranches(base, currentTip, last, newTip);

        // update tip
        database.updateTip(newTip);
    }

    private void writeFile(String hash, byte data[], int offset, int size) throws IOException {
        final String pathOrg = database.getPath();

        File dir = new File(pathOrg + "/objects" + hash.substring(0, 2));
        dir.mkdir();
        File file = new File(dir, hash.substring(2));
        if (file.exists())
            return;

        FileOutputStream outputStream = new FileOutputStream(file);
        try {
            outputStream.write(data, offset, size);
        } finally {
            outputStream.close();
        }
    }

    private String readTill(DataInputStream stream, char stopChar)
    {
        String out = "";

        while (true) {
            char data = 0;
            try {
                data = stream.readChar();
            } catch (IOException e) {
                return out;
            }
            if (data == stopChar)
                break;
            out += data;
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
                commits.add(parent.getId().toString());
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
            if (currentCommit == commitStop)
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

                String parentString = parent.getId().toString();
                // if we reachted the ancestor commits tree we are done
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
        DataOutputStream stream = new DataOutputStream(packageData);

        for (int i = 0; i < objects.size(); i++) {
            ObjectLoader loader = repository.getObjectDatabase().newReader().open(ObjectId.fromString(objects.get(i)));

            String typeString = Constants.typeString(loader.getType());
            stream.writeBytes(typeString);
            stream.write(' ');
            String sizeString = "";
            sizeString += loader.getSize();
            stream.writeBytes(sizeString);
            stream.write('\0');

            ByteArrayOutputStream zippedData = new ByteArrayOutputStream();
            DataOutputStream zipOutStream = new DataOutputStream(zippedData);
            InputStream inputStream = loader.openStream();
            GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
            int zipSize = 0;
            try {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = gzipInputStream.read(buffer)) > 0) {
                    zipOutStream.write(buffer, 0, length);
                    zipSize += length;
                }
            } finally {
                gzipInputStream.close();
            }

            stream.writeBytes(objects.get(i));
            stream.write(' ');
            String blobSize = "";
            blobSize += zipSize;
            stream.writeBytes(blobSize);
            stream.write('\0');
            byte blobCompressed[] = zippedData.toByteArray();
            stream.write(blobCompressed, 0, blobCompressed.length);
        }

        return packageData.toByteArray();
    }

    private byte[] exportPack(String commitOldest, String commitLatest, String ignoreCommit, int format) throws Exception {
        String commitEnd = new String(commitLatest);
        if (commitLatest == "")
            commitEnd = database.getTip();
        if (commitEnd == "")
            throw new Exception("no tip found");

        List<String> blobs = new ArrayList<>();
        collectMissingBlobs(commitOldest, commitEnd, ignoreCommit, blobs, -1);
        return packObjects(blobs);
    }

    private void listTreeObjects(RevTree tree, List<String> objects) throws IOException {

        String treeOidString = tree.getId().toString();
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
                String objectOidString = treeWalk.getObjectId(0).toString();
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
        final AbstractTreeIterator i = tw.getTree(tree, AbstractTreeIterator.class);
        if (i != null) {
            if (FileMode.TREE.equals(tw.getRawMode(tree))) {
                builder.addTree(tw.getRawPath(), stage, reader, tw
                        .getObjectId(tree));
            } else {
                final DirCacheEntry e;

                e = new DirCacheEntry(tw.getRawPath(), stage);
                e.setObjectIdFromRaw(i.idBuffer(), i.idOffset());
                e.setFileMode(tw.getFileMode(tree));
                builder.add(e);
            }
        }
    }

    private boolean nonTree(final int mode) {
        return mode != 0 && !FileMode.TREE.equals(mode);
    }

    private void mergeBranches(String baseCommit, String ours, String theirs, String merge) throws IOException {
        final ObjectReader reader = repository.getObjectDatabase().newReader();
        RevWalk walk = new RevWalk(repository);

        RevCommit baseRevCommit = walk.lookupCommit(ObjectId.fromString(baseCommit));
        RevCommit oursCommit = walk.lookupCommit(ObjectId.fromString(ours));
        RevCommit theirsCommit = walk.lookupCommit(ObjectId.fromString(theirs));

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

        ObjectInserter odi = repository.newObjectInserter();
        ObjectId resultTree = cache.writeTree(odi);
        odi.flush();

        RevTree newRootTree = walk.lookupTree(resultTree);
        ObjectId commitId = mergeCommit(newRootTree, oursCommit, theirsCommit);
        database.updateTip(commitId.toString());
    }

    private ObjectId mergeCommit(RevTree tree, RevCommit parent1, RevCommit parent2) throws IOException {
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

        return commitId;
    }
}
