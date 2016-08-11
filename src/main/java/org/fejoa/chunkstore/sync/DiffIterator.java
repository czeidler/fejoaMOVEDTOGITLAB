/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.library.database.StorageDir;

import java.util.*;


public class DiffIterator<T> implements Iterator<DiffIterator.Change> {
    public interface NameGetter<T> {
        String getName(T entry);
    }

    public enum Type {
        ADDED,
        REMOVED,
        MODIFIED
    }

    static public class Change<T> {
        public Type type;
        public String path;
        public T ours;
        public T theirs;

        private Change(Type type, String path) {
            this.type = type;
            this.path = path;
        }

        static public <T>Change added(String path, T theirs) {
            Change change = new Change(Type.ADDED, path);
            change.theirs = theirs;
            return change;
        }

        static public <T>Change removed(String path, T ours) {
            Change change = new Change(Type.REMOVED, path);
            change.ours = ours;
            return change;
        }

        static public <T>Change modified(String path, T ours, T theirs) {
            Change change = new Change(Type.MODIFIED, path);
            change.ours = ours;
            change.theirs = theirs;
            return change;
        }
    }

    private Comparator<T> entryComparator = new Comparator<T>() {
        @Override
        public int compare(T entry, T t1) {
            return nameGetter.getName(entry).compareTo(nameGetter.getName(t1));
        }
    };


    public DiffIterator(String basePath, Collection<T> ours, Collection<T> theirs, NameGetter<T> nameGetter) {
        this.nameGetter = nameGetter;
        this.basePath = basePath;

        if (ours != null)
            oursEntries = new ArrayList<>(ours);
        else
            oursEntries = new ArrayList<>();
        Collections.sort(oursEntries, entryComparator);
        theirsEntries = new ArrayList<>(theirs);
        Collections.sort(theirsEntries, entryComparator);

        gotoNext();
    }

    final NameGetter<T> nameGetter;
    final String basePath;
    final List<T> oursEntries;
    final List<T> theirsEntries;
    int ourIndex = 0;
    int theirIndex = 0;
    Change next = null;


    private void gotoNext() {
        next = null;
        while (next == null) {
            T ourEntry = null;
            T theirEntry = null;
            if (ourIndex < oursEntries.size())
                ourEntry = oursEntries.get(ourIndex);
            if (theirIndex < theirsEntries.size())
                theirEntry = theirsEntries.get(theirIndex);
            if (ourEntry == null && theirEntry == null)
                break;
            int compareValue;
            if (ourEntry == null)
                compareValue = 1;
            else if (theirEntry == null)
                compareValue = -1;
            else
                compareValue = entryComparator.compare(ourEntry, theirEntry);

            if (compareValue == 0) {
                theirIndex++;
                ourIndex++;
                if (!ourEntry.equals(theirEntry)) {
                    next = Change.modified(StorageDir.appendDir(basePath, nameGetter.getName(ourEntry)), ourEntry,
                            theirEntry);
                    break;
                }
                continue;
            } else if (compareValue > 0) {
                // added
                theirIndex++;
                next = Change.added(StorageDir.appendDir(basePath, nameGetter.getName(theirEntry)), theirEntry);
                break;
            } else {
                // removed
                ourIndex++;
                next = Change.removed(StorageDir.appendDir(basePath, nameGetter.getName(ourEntry)), ourEntry);
                break;

            }
        }
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Change next() {
        Change current = next;
        gotoNext();
        return current;
    }

    @Override
    public void remove() {

    }

}
