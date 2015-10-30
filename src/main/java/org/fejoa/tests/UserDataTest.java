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
import org.fejoa.library2.Remote;
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
        Remote remoteRemote = new Remote(user, server);
        userData.getRemoteList().add(remoteRemote);
        userData.getRemoteList().setDefault(remoteRemote);

        String defaultSignatureKey = userData.getIdentityStore().getDefaultSignatureKey();
        String defaultPublicKey = userData.getIdentityStore().getDefaultEncryptionKey();
        userData.commit();

        // open it again
        context = new FejoaContext(dir);
        userData = UserData.open(context, context.getStorage(id), password);

        assertEquals(1, userData.getStorageRefList().getEntries().size());

        assertEquals(1, userData.getRemoteList().getEntries().size());
        assertEquals(remoteRemote.getId(), userData.getRemoteList().getDefault().getId());

        assertEquals(defaultSignatureKey, userData.getIdentityStore().getDefaultSignatureKey());
        assertEquals(defaultPublicKey, userData.getIdentityStore().getDefaultEncryptionKey());
    }
}
