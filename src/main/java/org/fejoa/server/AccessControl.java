/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.database.JGitInterface;

import java.io.IOException;


public class AccessControl {
    static private boolean hasAccess(Session session, String user, String branch) {
        if (session.isRootUser(user))
            return true;
        // TODO access control
        return true;
    }

    static public JGitInterface getDatabase(Session session, String user, String branch) throws IOException {
        if (!hasAccess(session, user, branch))
            throw new IOException("access denied");
        JGitInterface gitInterface = new JGitInterface();
        gitInterface.init(session.getBaseDir() + "/" + user + "/.gitDatabase", branch, true);
        return gitInterface;
    }

}
