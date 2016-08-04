/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.support.DoubleLinkedList;


public class DoubleLinkedListTest extends TestCase {
    class TestEntry extends DoubleLinkedList.Entry {
        int i;

        TestEntry(int i) {
            this.i = i;
        }

        @Override
        public String toString() {
            return "" + i;
        }
    }

    private void assertContent(DoubleLinkedList<TestEntry> list, String expected) {
        assertEquals(expected.length(), list.size());

        String result = "";
        for (TestEntry entry : list)
            result += entry.toString();
        assertEquals(expected, result);

        if (list.getTail() != null)
            assertEquals(list.getTail().toString(), expected.substring(expected.length() - 1));
    }

    public void testLinkedList() {
        DoubleLinkedList<TestEntry> linkedList = new DoubleLinkedList<>();

        TestEntry entry1 = new TestEntry(1);
        TestEntry entry2 = new TestEntry(2);
        TestEntry entry3 = new TestEntry(3);
        TestEntry entry4 = new TestEntry(4);
        TestEntry entry5 = new TestEntry(5);
        linkedList.addFirst(entry4);
        assertContent(linkedList, "4");
        linkedList.addBefore(entry2, entry4);
        assertContent(linkedList, "24");
        linkedList.addBefore(entry3, entry4);
        assertContent(linkedList, "234");
        linkedList.addFirst(entry1);
        assertContent(linkedList, "1234");
        linkedList.addLast(entry5);
        assertContent(linkedList, "12345");

        linkedList.remove(entry5);
        assertContent(linkedList, "1234");
        linkedList.remove(entry1);
        assertContent(linkedList, "234");
        linkedList.remove(entry3);
        assertContent(linkedList, "24");
        linkedList.remove(entry4);
        assertContent(linkedList, "2");
        linkedList.remove(entry2);
        assertContent(linkedList, "");
    }
}
