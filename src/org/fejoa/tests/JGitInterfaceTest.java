/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.StorageLib;
import org.fejoa.library.git.JGitInterface;

import java.io.File;
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

    public void testInitWriteRead() {
        testInitWriteReadSimple("gitTest", "test");
    }

    public void testInitWriteReadSubdirs() {
        testInitWriteReadSimple("gitTest2", "sub/dir/test");
    }

    private void testInitWriteReadSimple(String gitDir, String dataPath) {
        cleanUpDirs.add(gitDir);

        JGitInterface git = new JGitInterface();
        git.init(gitDir, "testBranch", true);

        String testString = "Hello jGit!";
        try {
            git.writeBytes(dataPath, testString.getBytes());
            git.commit();

            // and read again
            String result = git.readString(dataPath);
            assertEquals(testString, result);
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }
    }
}