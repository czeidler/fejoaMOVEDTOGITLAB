/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.RemoteList;
import org.fejoa.library2.UserData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class UserDataTest extends TestCase {
    final List<String> cleanUpDirs = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testUserData() throws IOException, CryptoException {
        String dir = "userDataTest";
        cleanUpDirs.add(dir);

        String password = "password";
        String user = "user";
        String server = "localhost";

        FejoaContext context = new FejoaContext(dir);
        UserData userData = UserData.create(context, password);
        String id = userData.getId();
        RemoteList.Entry remoteEntry = new RemoteList.Entry(user, server);
        userData.getRemoteList().add(remoteEntry);
        userData.getRemoteList().setDefault(remoteEntry);
        userData.commit();

        // open it again
        context = new FejoaContext(dir);
        userData = UserData.open(context, context.getStorage(id), password);

        assertEquals(1, userData.getStorageList().getEntries().size());

        assertEquals(1, userData.getRemoteList().getEntries().size());
        assertEquals(remoteEntry.getId(), userData.getRemoteList().getDefault().getId());
    }
}
