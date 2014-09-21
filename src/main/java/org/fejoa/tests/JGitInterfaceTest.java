/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.database.JGitInterface;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JGitInterfaceTest extends TestCase {
    final List<String> cleanUpDirs = new ArrayList<String>();

    @Override
    public void setUp() throws Exception {
        super.setUp();

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testInitWriteRead() throws Exception {
        testInitWriteReadSimple("gitTest", "test");
    }

    public void testInitWriteReadSubdirs() throws Exception {
        testInitWriteReadSimple("gitTest2", "sub/dir/test");
    }

    private void testInitWriteReadSimple(String gitDir, String dataPath) throws Exception {
        cleanUpDirs.add(gitDir);

        JGitInterface git = new JGitInterface();
        git.init(gitDir, "testBranch", true);

        String testString = "Hello jGit!";
        try {
            git.writeBytes(dataPath, testString.getBytes());
            git.commit();

            // and read again
            String result = new String(git.readBytes(dataPath));
            assertEquals(testString, result);
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }
    }

    public void testListEntries() throws IOException {
        String gitDir = "listEntriesGit";
        cleanUpDirs.add(gitDir);

        JGitInterface git = new JGitInterface();
        git.init(gitDir, "testBranch", true);

        byte data[] = "test".getBytes();
        git.writeBytes("test1", data);
        git.writeBytes("test2", data);

        git.writeBytes("dir/test3", data);
        git.writeBytes("dir/test4", data);

        git.writeBytes("dir2/test5", data);
        git.writeBytes("dir2/test6", data);

        git.writeBytes("dir/sub1/test7", data);
        git.writeBytes("dir/sub1/test8", data);

        git.writeBytes("dir/sub2/sub3/test9", data);
        git.writeBytes("dir/sub2/sub3/test10", data);


        git.commit();

        List<String> entries = git.listDirectories("");
        assertTrue(equals(entries, Arrays.asList("dir", "dir2")));

        entries = git.listDirectories("dir");
        assertTrue(equals(entries, Arrays.asList("sub1", "sub2")));

        entries = git.listDirectories("dir/sub2");
        assertTrue(equals(entries, Arrays.asList("sub3")));

        entries = git.listFiles("");
        assertTrue(equals(entries, Arrays.asList("test1", "test2")));

        entries = git.listFiles("dir");
        assertTrue(equals(entries, Arrays.asList("test3", "test4")));

        entries = git.listFiles("dir/sub1");
        assertTrue(equals(entries, Arrays.asList("test7", "test8")));

        entries = git.listFiles("dir/sub2/sub3");
        assertTrue(equals(entries, Arrays.asList("test9", "test10")));
    }

    private boolean equals(List<String> list1, List<String> list2) {
        return list1.containsAll(list2) && list2.containsAll(list1);
    }

    class DatabaseStingEntry {
        public String path;
        public String content;

        public DatabaseStingEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    private void add(JGitInterface database, List<DatabaseStingEntry> content, DatabaseStingEntry entry)
            throws Exception {
        content.add(entry);
        database.writeBytes(entry.path, entry.content.getBytes());
    }

    private boolean containsContent(JGitInterface database, List<DatabaseStingEntry> content) throws IOException {
        for (DatabaseStingEntry entry : content) {
            byte bytes[] = database.readBytes(entry.path);
            if (!entry.content.equals(new String(bytes)))
                return false;
        }
        return true;
    }

    public void testExportImport() throws Exception {
        List<DatabaseStingEntry> content = new ArrayList<>();

        String gitDir = "gitExport";
        cleanUpDirs.add(gitDir);

        JGitInterface gitExport = new JGitInterface();
        gitExport.init(gitDir, "testBranch", true);

        add(gitExport, content, new DatabaseStingEntry("test1", "data1"));
        String commit1 = gitExport.commit();
        assertEquals(commit1, gitExport.getTip());

        add(gitExport, content, new DatabaseStingEntry("folder/test2", "data2"));
        String commit2 = gitExport.commit();
        assertEquals(commit2, gitExport.getTip());

        assertTrue(containsContent(gitExport, content));

        // init import database
        gitDir = "gitImport";
        cleanUpDirs.add(gitDir);
        JGitInterface gitImport = new JGitInterface();
        gitImport.init(gitDir, "testBranch", true);

        sync(gitExport, gitImport);
        assertTrue(containsContent(gitImport, content));

        // test with merge

        // create non conflicting fork
        add(gitExport, content, new DatabaseStingEntry("folder/test3", "data3"));
        gitExport.commit();
        add(gitExport, content, new DatabaseStingEntry("test4", "data4"));
        gitExport.commit();

        add(gitImport, content, new DatabaseStingEntry("folder/test5", "data5"));
        gitImport.commit();
        add(gitImport, content, new DatabaseStingEntry("test6", "data6"));
        gitImport.commit();

        sync(gitExport, gitImport);
        assertTrue(containsContent(gitImport, content));
    }

    private void sync(JGitInterface gitExport, JGitInterface gitImport) throws Exception {
        String tip = gitExport.getTip();
        byte exportData[] = gitExport.exportPack("", tip, "", -1);

        String base = gitExport.getLastSyncCommit("gitImport", gitExport.getBranch());
        gitImport.importPack(exportData, base, tip, -1);

        gitExport.updateLastSyncCommit("gitImport", gitExport.getBranch(), tip);
    }
}