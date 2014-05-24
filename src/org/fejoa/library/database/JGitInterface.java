/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.*;

class TreeBuilder {
    static public ObjectId updateNode(Repository repository, ObjectId rootTree, String path, byte[] data)
            throws Exception
    {
        String paths[] = path.split("/");
        if (paths.length == 0)
            throw new IllegalArgumentException();

        // write the blob
        ObjectInserter objectInserter = repository.newObjectInserter();
        ObjectId blobId = objectInserter.insert(Constants.OBJ_BLOB, data);
        FileMode mode = FileMode.REGULAR_FILE;

        for (int i = paths.length - 1; i >= 0; i--) {
            String currentPath = paths[i];
            TreeWalk treeWalk = null;
            if (!rootTree.equals(ObjectId.zeroId()))
                treeWalk = TreeWalk.forPath(repository, buildPath(paths, i), rootTree);
            TreeFormatter treeFormatter = new TreeFormatter();
            treeFormatter.append(currentPath, mode, blobId);
            if (treeWalk != null) {
                AbstractTreeIterator it = treeWalk.getTree(0, CanonicalTreeParser.class);
                while (!it.eof()) {
                    byte buffer[] = new byte[it.getNameLength()];
                    it.getName(buffer, 0);
                    treeFormatter.append(buffer, mode, it.getEntryObjectId());
                    it.next(1);
                }
            }

            blobId = treeFormatter.insertTo(objectInserter);
            // from now on add directories
            mode = FileMode.TREE;
        }
        objectInserter.flush();
        return blobId;
    }

    static private String buildPath(String[] paths, int n) {
        String path = "";
        for (int i = 0; i <= n; i++) {
            if (i > 0)
                path += "/";
            path += paths[i];
        }
        return path;
    }
}

public class JGitInterface implements IDatabaseInterface {
    private Repository repository = null;
    private String branch = "";
    private ObjectId rootTree = ObjectId.zeroId();

    @Override
    public void init(String path, String branch, boolean create) throws IOException {
        this.branch = branch;
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(path))
                .readEnvironment()
                .findGitDir()
                .build();

        File dir = new File(path);
        if (dir.exists())
            return;

        if (create)
            repository.create(create);
    }

    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public byte[] readBytes(String path) throws IOException{
        RevWalk walk = new RevWalk(repository);
        ObjectId tree;
        if (!rootTree.equals(ObjectId.zeroId()))
            tree = rootTree;
        else {
            RevCommit commit = walk.parseCommit(repository.getRef(branch).getLeaf().getObjectId());
            if (commit == null)
                throw new IOException();
            tree = commit.getTree().toObjectId();
        }
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(path));
        if (!treeWalk.next()) {
            return null;
        }
        ObjectId objectId = treeWalk.getObjectId(0);
        return repository.open(objectId).getBytes();
    }

    @Override
    public void writeBytes(String path, byte[] bytes) throws Exception {
        rootTree = TreeBuilder.updateNode(repository, rootTree, path, bytes);
    }

    @Override
    public void commit() throws Exception {
        if (rootTree.equals(ObjectId.zeroId()))
            throw new InvalidObjectException("invalid root tree");

        CommitBuilder commit = new CommitBuilder();
        PersonIdent personIdent = new PersonIdent("client", "");
        commit.setCommitter(personIdent);
        commit.setAuthor(personIdent);
        commit.setMessage("client commit");
        commit.setTreeId(rootTree);

        ObjectInserter objectInserter = repository.newObjectInserter();

        ObjectId commitId = objectInserter.insert(commit);
        objectInserter.flush();
        RefUpdate refUpdate = repository.updateRef("refs/heads/" + getBranch());
        refUpdate.setForceUpdate(true);
        refUpdate.setRefLogIdent(personIdent);
        refUpdate.setNewObjectId(commitId);
        refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
        refUpdate.setRefLogMessage("client commit", false);
        RefUpdate.Result result = refUpdate.update();
        if (result == RefUpdate.Result.REJECTED)
            throw new IOException();
        rootTree = ObjectId.zeroId();
    }
}
