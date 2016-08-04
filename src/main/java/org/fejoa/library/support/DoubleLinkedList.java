/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;


import java.util.Iterator;

public class DoubleLinkedList<T extends DoubleLinkedList.Entry> implements Iterable<T> {
    public static class Entry {
        protected Entry previous;
        protected Entry next;
    }

    private Entry head;
    private Entry tail;
    private int nEntries = 0;

    public T getHead() {
        return (T)head;
    }

    public T getTail() {
        return (T)tail;
    }

    public int size() {
        return nEntries;
    }

    public void addBefore(T entry, T next) {
        nEntries++;

        entry.next = null;
        entry.previous = null;
        if (head == null) {
            head = entry;
            tail = head;
            return;
        }
        if (next.previous != null)
            next.previous.next = entry;
        entry.previous = next.previous;
        entry.next = next;
        next.previous = entry;
        if (next == head)
            head = entry;
    }

    public void addFirst(T entry) {
        addBefore(entry, getHead());
    }

    public void addLast(T entry) {
        nEntries++;

        entry.next = null;
        entry.previous = null;
        if (head == null) {
            head = entry;
            tail = head;
            return;
        }

        entry.previous = tail;
        tail.next = entry;
        tail = entry;
    }

    public void remove(Entry entry) {
        nEntries--;

        if (entry.previous != null)
            entry.previous.next = entry.next;
        else
            head = entry.next;

        if (entry == tail)
            tail = entry.previous;

        if (entry.next != null)
            entry.next.previous = entry.previous;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            Entry current = head;

            @Override
            public void remove() {
                DoubleLinkedList.this.remove(current);
                current = current.next;
            }

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public T next() {
                Entry entry = current;
                current = entry.next;
                return (T)entry;
            }
        };
    }

}
