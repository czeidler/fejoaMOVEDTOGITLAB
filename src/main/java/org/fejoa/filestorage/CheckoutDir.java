/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.StreamHelper;
import org.fejoa.library.support.Task;
import org.json.JSONException;

import java.io.*;
import java.security.DigestOutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public class CheckoutDir {
    public static class Update {
        final public File file;

        public Update(File file) {
            this.file = file;
        }
    }

    public static class Result {

    }

    final private StorageDir storageDir;
    final private Index index;
    final private File destination;

    public CheckoutDir(StorageDir storageDir, Index index, File destination) {
        this.storageDir = storageDir;
        this.index = index;
        this.destination = destination;
    }

    public Task<Update, Result> checkOut() {
        Task<Update, Result> task = new Task<>(new Task.ITaskFunction<Update, Result>() {
            @Override
            public void run(Task<Update, Result> task) throws Exception {
                performCheckOut(task, "");
                index.commit();
            }

            @Override
            public void cancel() {

            }
        });
        return task;
    }

    public Task<Update, Result> checkIn() {
        Task<Update, Result> task = new Task<>(new Task.ITaskFunction<Update, Result>() {
            @Override
            public void run(Task<Update, Result> task) throws Exception {
                performCheckIn(task, "");
                storageDir.commit();
                index.commit();
            }

            @Override
            public void cancel() {

            }
        });
        return task;
    }

    private void performCheckOut(Task<Update, Result> task, String dir) throws IOException, JSONException {
        List<String> files = storageDir.listFiles(dir);
        // checkout files
        File targetDir = new File(destination, dir);
        targetDir.mkdirs();

        List<String> indexedFiles;
        try {
            indexedFiles = index.listFiles(dir);
        } catch (IOException e) {
            indexedFiles = new ArrayList<>();
        }
        for (String file : files) {
            String filePath = StorageDir.appendDir(dir, file);
            File outFile = new File(targetDir, file);

            if (indexedFiles.contains(file) && !needsCheckout(outFile,  storageDir.getHash(filePath),
                    index.get(filePath)))
                continue;

            outFile.createNewFile();
            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outFile, false));
            outputStream.write(storageDir.readBytes(filePath));
            outputStream.close();

            HashValue hash = storageDir.getHash(filePath);
            index.update(filePath, new Index.Entry(hash, outFile));

            task.onProgress(new Update(outFile));
        }

        List<String> subDirs = storageDir.listDirectories(dir);
        for (String subDir : subDirs)
            performCheckOut(task, StorageDir.appendDir(dir, subDir));
    }

    private void performCheckIn(Task<Update, Result> task, String dir) throws IOException, JSONException {
        File checkOutDir = new File(destination, dir);

        File[] checkOutDirContent = checkOutDir.listFiles();
        List<String> files = new ArrayList<>(storageDir.listFiles(dir));
        List<String> dirs = new ArrayList<>(storageDir.listDirectories(dir));

        // checkout files
        for (File checkedOutFile : checkOutDirContent) {
            String name = checkedOutFile.getName();
            String filePath = StorageDir.appendDir(dir, name);
            if (checkedOutFile.isFile()) {
                files.remove(name);
                if (checkoutFileChanged(checkedOutFile, index.get(filePath))) {
                    // write to database
                    BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(checkedOutFile));
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    StreamHelper.copy(inputStream, outputStream);
                    storageDir.writeBytes(filePath, outputStream.toByteArray());
                    index.update(filePath, new Index.Entry(storageDir.getHash(filePath), checkedOutFile));
                    task.onProgress(new Update(checkedOutFile));
                }
            } else {
                dirs.remove(name);
                performCheckIn(task, filePath);
            }
        }

        // remaining entries have been removed
        for (String removedFile : files) {
            String filePath = StorageDir.appendDir(dir, removedFile);
            storageDir.remove(filePath);
            index.remove(filePath);
        }
        for (String removedDir : dirs) {
            String filePath = StorageDir.appendDir(dir, removedDir);
            storageDir.remove(filePath);
            index.remove(filePath);
        }
    }

    private boolean checkoutFileChanged(File outFile, Index.Entry entry) throws IOException {
        long length = outFile.length();
        if (length != entry.getLength())
            return true;

        long lastModified = outFile.lastModified();
        if (lastModified > entry.getLastModified())
            return true;
        else if (lastModified == entry.getLastModified()) {
            // compare the hash
            //TODO the hash in the entry is the git blob hash so it can't be compared to the file hash...
            return false;

            /*try {
                BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(outFile));
                DigestOutputStream outputStream = new DigestOutputStream(new OutputStream() {
                    @Override
                    public void write(int i) throws IOException {

                    }
                }, CryptoHelper.sha1Hash());
                StreamHelper.copy(inputStream, outputStream);
                HashValue hashValue = new HashValue(outputStream.getMessageDigest().digest());
                if (!hashValue.equals(entry.getHash()))
                    return true;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }*/
        }
        return false;
    }

    private boolean needsCheckout(File outFile, HashValue inDatabaseHash, Index.Entry entry) throws IOException {
        if (!outFile.exists())
            return true;
        if (!inDatabaseHash.equals(entry.getHash()))
            return true;

        return checkoutFileChanged(outFile, entry);
    }
}
