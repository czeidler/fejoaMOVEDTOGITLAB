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
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


class GitTree {
    private Repository repository = null;
    private Map<String, ObjectId> subDirs = new HashMap<>();
    private Map<String, ObjectId> files = new HashMap<>();

    public GitTree(Repository repository) {
        this.repository = repository;
    }

    public void setTo(ObjectId dir) throws IOException {
        clear();

        byte[] data = repository.open(dir).getBytes();

        int start = 0;
        while (start < data.length) {

            int pos = findFirst(data, (byte)0, start);
            String line = new String(data, start, pos - start);
            String[] list = line.split(" ", 2);
            String name = list[1];
            byte[] object = new byte[20];
            System.arraycopy(data, pos + 1, object, 0, 20);
            start = pos + 21;
            ObjectId objectId = ObjectId.fromRaw(object);

            int mode = Integer.parseInt(list[0], 8);
            if ((mode & 040000) != 0)
                subDirs.put(name, objectId);
            else if (mode == 57344) {

            } else
                files.put(name, objectId);
        }
    }

    public ObjectId findFile(ObjectId baseDir, String path) throws IOException {
        setTo(baseDir);

        String[] split = path.split("/");
        if (split.length == 0)
            return null;

        // walk dir
        for (int i = 0; i < split.length - 1; i++) {
            String searchDir = split[i];
            ObjectId subDir = subDirs.get(searchDir);
            if (subDir == null)
                return null;
            setTo(subDir);
        }

        return files.get(split[split.length - 1]);
    }

    private int findFirst(byte[] data, byte b, int start) {
        int pos = start;
        while (pos < data.length) {
            if (data[pos] == b)
                return pos;
            pos++;
        }
        return -1;
    }

    private void clear() {
        subDirs.clear();
        files.clear();
    }
}

public class JGitInterface implements IDatabaseInterface {
    private Repository repository = null;
    private String path = "";
    private String branch = "";
    private ObjectId rootTree = ObjectId.zeroId();

    @Override
    public void init(String path, String branch, boolean create) throws IOException {
        this.path = path;
        this.branch = branch;
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(path))
                .readEnvironment()
                .findGitDir()
                .build();

        File dir = new File(path);
        if (!dir.exists()) {
            if (create)
                repository.create(create);
            else
                return;;
        }

        // get root tree if existing
        Ref branchRef = repository.getRef(branch);
        if (branchRef != null) {
            RevWalk walk = new RevWalk(repository);
            RevCommit commit = walk.parseCommit(branchRef.getLeaf().getObjectId());
            if (commit == null)
                throw new IOException();
            rootTree = commit.getTree().toObjectId();
        }
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public byte[] readBytes(String path) throws IOException{
        TreeWalk treeWalk = cdFile(path);
        if (!treeWalk.next())
            throw new FileNotFoundException();

        ObjectId objectId = treeWalk.getObjectId(0);
        return repository.open(objectId).getBytes();
    }

    @Override
    public void writeBytes(String path, byte[] bytes) throws IOException {
        // first remove old entry if there is any
        try {
            rmFile(path);
        } catch(IOException e) {

        }

        // write the blob
        final ObjectInserter objectInserter = repository.newObjectInserter();
        final ObjectId blobId = objectInserter.insert(Constants.OBJ_BLOB, bytes);

        final DirCache cache = DirCache.newInCore();
        final DirCacheBuilder builder = cache.builder();
        if (!rootTree.equals(ObjectId.zeroId())) {
            final ObjectReader reader = repository.getObjectDatabase().newReader();
            builder.addTree("".getBytes(), DirCacheEntry.STAGE_0, reader, rootTree);
        }
        final DirCacheEntry entry = new DirCacheEntry(path);

        entry.setLastModified(System.currentTimeMillis());

        entry.setFileMode(FileMode.REGULAR_FILE);
        entry.setObjectId(blobId);

        builder.add(entry);
        builder.finish();

        rootTree = cache.writeTree(objectInserter);
        objectInserter.flush();
    }

    @Override
    public String commit() throws IOException {
        if (rootTree.equals(ObjectId.zeroId()))
            throw new InvalidObjectException("invalid root tree");

        CommitBuilder commit = new CommitBuilder();
        PersonIdent personIdent = new PersonIdent("client", "");
        commit.setCommitter(personIdent);
        commit.setAuthor(personIdent);
        commit.setMessage("client commit");
        commit.setTreeId(rootTree);
        String tip = getTip();
        ObjectId oldTip;
        if (tip.equals(""))
            oldTip = ObjectId.zeroId();
        else {
            oldTip = ObjectId.fromString(tip);
            commit.setParentId(oldTip);
        }

        ObjectInserter objectInserter = repository.newObjectInserter();
        ObjectId commitId = objectInserter.insert(commit);
        objectInserter.flush();

        RefUpdate refUpdate = repository.updateRef("refs/heads/" + getBranch());
        refUpdate.setForceUpdate(true);
        refUpdate.setRefLogIdent(personIdent);
        refUpdate.setNewObjectId(commitId);
        refUpdate.setExpectedOldObjectId(oldTip);
        refUpdate.setRefLogMessage("client commit", false);
        RefUpdate.Result result = refUpdate.update();
        if (result == RefUpdate.Result.REJECTED)
            throw new IOException();

        return commitId.name();
    }

    private TreeWalk cdFile(String path) throws IOException {
        RevWalk walk = new RevWalk(repository);
        ObjectId tree;
        if (!rootTree.equals(ObjectId.zeroId()))
            tree = rootTree;
        else {
            Ref branchRef = repository.getRef(branch);
            if (branchRef == null)
                throw new IOException();
            RevCommit commit = walk.parseCommit(branchRef.getLeaf().getObjectId());
            if (commit == null)
                throw new IOException();
            tree = commit.getTree().toObjectId();
        }
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(path));

        return treeWalk;
    }

    private TreeWalk cd(String path) throws IOException {
        RevWalk walk = new RevWalk(repository);
        ObjectId tree;
        if (!rootTree.equals(ObjectId.zeroId()))
            tree = rootTree;
        else {
            Ref branchRef = repository.getRef(branch);
            if (branchRef == null)
                throw new IOException();
            RevCommit commit = walk.parseCommit(branchRef.getLeaf().getObjectId());
            if (commit == null)
                throw new IOException();
            tree = commit.getTree().toObjectId();
        }
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        if (!path.equals("") && !path.equals("/"))
            treeWalk.setFilter(PathFilter.create(path));
        return treeWalk;
    }

    @Override
    public List<String> listFiles(String path) throws IOException {
        List<String> files = new ArrayList<>();

        TreeWalk treeWalk = cd(path);

        String pathList[] = path.split("/");
        int nDirs = 0;
        if (!path.equals("") && !path.equals("/"))
            nDirs = pathList.length;
        while (treeWalk.next()) {
            if (treeWalk.isSubtree())
                continue;
            String currentPath = treeWalk.getPathString();
            String currentPathList[] = currentPath.split("/");
            if (currentPathList.length == nDirs + 1)
                files.add(treeWalk.getNameString());
        }

        return files;
    }

    @Override
    public List<String> listDirectories(String path) throws IOException {
        // TODO: this is horrible slow!!
        List<String> dirs = new ArrayList<>();

        TreeWalk treeWalk = cd(path);

        String pathList[] = path.split("/");
        int nDirs = 0;
        if (!path.equals("") && !path.equals("/"))
            nDirs = pathList.length;
        while (treeWalk.next()) {
            String currentPath = treeWalk.getPathString();
            String currentPathList[] = currentPath.split("/");
            int nParts = currentPathList.length;
            if (nParts - 1 > nDirs){
                String dir = currentPathList[nDirs];
                if (!dirs.contains(dir))
                    dirs.add(dir);
            }
        }

        return dirs;
    }

    @Override
    public String getTip() throws IOException {
        Ref head = repository.getRef("refs/heads/" + branch);
        if (head == null)
            return "";
        return head.getObjectId().name();
    }

    @Override
    public void updateTip(String commit) throws IOException {
        String refPath = getPath() + "/refs/heads/";
        refPath += branch;

        PrintWriter out = new PrintWriter(new FileWriter(new File(refPath), false));
        try {
            out.println(commit);
        } finally {
            out.close();
        }
    }

    @Override
    public void remove(String path) throws IOException {
        if (isDirectory(path))
            rmDirectory(path);
        else
            rmFile(path);
    }

    private boolean isDirectory(String path) throws IOException {
        TreeWalk treeWalk = cd(path);
        treeWalk.next();
        // it's a directory if there is a second entry
        return treeWalk.next();
    }

    private void rm(DirCacheEditor.PathEdit operation) throws IOException {
        final DirCache cache = DirCache.newInCore();
        final DirCacheBuilder builder = cache.builder();
        if (!rootTree.equals(ObjectId.zeroId())) {
            final ObjectReader reader = repository.getObjectDatabase().newReader();
            builder.addTree("".getBytes(), DirCacheEntry.STAGE_0, reader, rootTree);
        }
        builder.finish();

        final DirCacheEditor cacheEditor = cache.editor();
        cacheEditor.add(operation);
        cacheEditor.finish();

        final ObjectInserter objectInserter = repository.newObjectInserter();
        rootTree = cache.writeTree(objectInserter);
        objectInserter.flush();
    }

    private void rmFile(String filePath) throws IOException {
        rm(new DirCacheEditor.DeletePath(filePath));
    }

    private void rmDirectory(String path) throws IOException {
        rm(new DirCacheEditor.DeleteTree(path));
    }

    @Override
    public String getLastSyncCommit(String remoteName, String remoteBranch) throws IOException {
        String refPath = new String(path);
        refPath += "/refs/remotes/";
        refPath += remoteName;
        refPath += "/";
        refPath += remoteBranch;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(refPath))));
            return reader.readLine();
        } catch (Exception e) {
            return "";
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    @Override
    public void updateLastSyncCommit(String remoteName, String remoteBranch, String uid) throws IOException {
        String refPath = new String(path);
        refPath += "/refs/remotes/";
        refPath += remoteName;
        File dir = new File(refPath);
        dir.mkdirs();

        File file = new File(dir, remoteBranch);
        PrintWriter out = new PrintWriter(file);
        try {
            out.println(uid);
        } finally {
            out.close();
        }
    }

    @Override
    public byte[] exportPack(String startCommit, String endCommit, String ignoreCommit, int format) throws IOException {
        PackManager packManager = new PackManager(this, repository);
        return packManager.exportPack(startCommit, endCommit, ignoreCommit, -1);
    }

    @Override
    public void importPack(byte[] pack, String baseCommit, String endCommit, int format) throws IOException {
        if (endCommit.length() != 40)
            throw new IllegalArgumentException();

        PackManager packManager = new PackManager(this, repository);
        packManager.importPack(pack, baseCommit, endCommit, format);

        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(repository.getRef(branch).getLeaf().getObjectId());
        rootTree = commit.getTree().getId();
    }
}
