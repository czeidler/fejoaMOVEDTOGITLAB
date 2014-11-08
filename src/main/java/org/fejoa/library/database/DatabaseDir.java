/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java.util.ArrayList;
import java.util.List;


public class DatabaseDir {
    final private String directoryName;
    final private List<String> files = new ArrayList<>();
    final private List<DatabaseDir> directories = new ArrayList<>();

    public DatabaseDir(String dirName) {
        this.directoryName = dirName;
    }

    public boolean isEmpty() {
        if (files.size() > 0)
            return false;
        if (directories.size() > 0)
            return false;
        return true;
    }

    public String getDirName() {
        return directoryName;
    }

    public DatabaseDir findDirectory(String path) {
        if (path.equals(""))
            return this;

        String[] parts = path.split("/");
        DatabaseDir currentDir = this;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            currentDir = currentDir.getChildDirectory(part);
        if (currentDir == null)
            return null;
    }
        return currentDir;
    }

    public List<String> getFiles() {
        return files;
    }

    public List<DatabaseDir> getDirectories() {
        return directories;
    }

    public void addPath(String path) {
        String[] parts = path.split("/");
        DatabaseDir currentDir = this;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == parts.length - 1) {
                currentDir.files.add(part);
                return;
            }

            DatabaseDir child = currentDir.getChildDirectory(part);
            if (child == null) {
                child = new DatabaseDir(part);
                currentDir.directories.add(child);
            }
            currentDir = child;
        }
    }

    public List<String> getChildDirectories() {
        List<String> directoryList = new ArrayList<>();
        for (DatabaseDir childDirectory : directories)
            directoryList.add(childDirectory.directoryName);
        return directoryList;
    }

    public DatabaseDir getChildDirectory(String dirName) {
        for (DatabaseDir databaseDir : directories) {
            if (databaseDir.directoryName.equals(dirName))
                return databaseDir;
        }
        return null;
    }
}
