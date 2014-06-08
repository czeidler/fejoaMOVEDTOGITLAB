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

        String tip1 = gitExport.getTip();
        byte exportData[] = gitExport.exportPack("", tip1, "", -1);

        gitDir = "gitImport";
        cleanUpDirs.add(gitDir);
        JGitInterface gitImport = new JGitInterface();
        gitImport.init(gitDir, "testBranch", true);

        gitImport.importPack(exportData, "", tip1, -1);

        assertTrue(containsContent(gitImport, content));
    }
}