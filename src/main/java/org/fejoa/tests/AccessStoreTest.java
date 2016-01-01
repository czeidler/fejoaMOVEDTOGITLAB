/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library2.*;
import org.fejoa.library2.database.StorageDir;
import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class AccessStoreTest extends TestCase {
    final static String TEST_DIR = "accessStoreTest";

    final private List<String> cleanUpDirs = new ArrayList<>();

    @Override
    public void setUp() throws Exception {
        super.setUp();

        cleanUpDirs.add(TEST_DIR);
        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testSimple() throws Exception {
        // set up
        FejoaContext context = new FejoaContext(TEST_DIR);
        StorageDir serverDir = context.getStorage("server");

        // create token
        AccessToken accessToken = AccessToken.create(context);
        AccessRight accessRight = new AccessRight("branch");
        accessRight.setGitAccessRights(AccessRight.PULL);
        JSONArray accessRights = new JSONArray();
        accessRights.put(accessRight.toJson());
        accessToken.setAccessEntry(accessRights.toString());
        accessToken.write(serverDir);
        serverDir.commit();

        // test to reopen it
        serverDir = context.getStorage("server");
        accessToken = AccessToken.open(context, serverDir);
        String contactToken = accessToken.getContactToken().toString();

        // pass it to the contact
        AccessTokenContact accessTokenContact = new AccessTokenContact(context, contactToken);
        assertEquals(accessRights.toString(), accessTokenContact.getAccessEntry());

        // let the server verify the access
        AccessTokenServer accessTokenServer = new AccessTokenServer(context, serverDir);
        final String authToken = "testToken";
        byte[] authSignature = accessTokenContact.signAuthToken(authToken);
        assertTrue(accessTokenServer.auth(authToken, authSignature));
        assertTrue(accessTokenServer.verify(accessTokenContact.getAccessEntry(),
                accessTokenContact.getAccessEntrySignature()));
    }
}
