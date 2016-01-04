/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.database.JGitInterface;
import org.fejoa.library2.BranchAccessRight;
import org.fejoa.library2.remote.StartMigrationJob;

import java.io.File;
import java.io.IOException;


public class AccessControl {
    final private Session session;
    final private String user;
    final private boolean isMigrating;

    public AccessControl(Session session, String user) {
        this.session = session;
        this.user = user;

        File migrationFile = new File(session.getServerUserDir(user), StartMigrationHandler.MIGRATION_INFO_FILE);
        this.isMigrating = migrationFile.exists();
    }

    private boolean hasAccess(String branch, int rights) {
        if (session.isRootUser(user))
            return true;
        // TODO access control
        return true;
    }

    public boolean canStartMigration() {
        return session.isRootUser(user);
    }

    public boolean isRootUser() {
        return session.isRootUser(user);
    }

    public JGitInterface getDatabase(String branch, int rights) throws IOException {
        if (!hasAccess(branch, rights))
            return null;
        JGitInterface gitInterface = new JGitInterface();
        gitInterface.init(session.getBaseDir() + "/" + user + "/.git", branch, true);
        return gitInterface;
    }

    public JGitInterface getReadDatabase(String branch) throws IOException {
        return getDatabase(branch, BranchAccessRight.PULL);
    }
}
