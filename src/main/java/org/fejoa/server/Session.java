/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library2.AccessTokenServer;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.UserData;
import org.fejoa.library2.command.IncomingCommandQueue;
import org.fejoa.library2.database.StorageDir;
import org.fejoa.library2.remote.CreateAccountJob;
import org.json.JSONObject;

import javax.servlet.http.HttpSession;
import java.util.HashSet;


public class Session {
    final static String ROLES_KEY = "roles";

    final private String baseDir;
    final private HttpSession session;

    public Session(String baseDir, HttpSession session) {
        this.baseDir = baseDir;
        this.session = session;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public String getServerUserDir(String serverUser) {
        return getBaseDir() + "/" + serverUser;
    }

    private String makeRole(String serverUser, String role) {
        return serverUser + ":" + role;
    }

    public boolean isRootUser(String serverUser) {
        return getRoles().contains(makeRole(serverUser, "root"));
    }

    public void addRole(String serverUser, String role) {
        HashSet<String> roles = getRoles();
        roles.add(makeRole(serverUser, role));
        session.setAttribute(ROLES_KEY, roles);
    }

    public HashSet<String> getRoles() {
        HashSet<String> roles = (HashSet<String>)session.getAttribute(ROLES_KEY);
        if (roles == null)
            return new HashSet<>();
        return roles;
    }

    public AccountSettings getAccountSettings(String serverUser) {
        return new AccountSettings(getServerUserDir(serverUser));
    }

    private String getUserDataBranch(String serverUser) throws Exception {
        JSONObject settings = getAccountSettings(serverUser).getSettings();
        if (!settings.has(CreateAccountJob.USER_DATA_BRANCH_KEY))
            throw new Exception("No user data branch set");
        return settings.getString(CreateAccountJob.USER_DATA_BRANCH_KEY);
    }

    public IncomingCommandQueue getIncomingCommandQueue(String serverUser) throws Exception {
        String userDataBranch = getUserDataBranch(serverUser);
        FejoaContext context = new FejoaContext(getServerUserDir(serverUser));
        StorageDir userDataDir = context.getStorage(userDataBranch);
        String incomingQueueBranch = userDataDir.readString(UserData.IN_COMMAND_QUEUE_ID_KEY);
        StorageDir incomingQueueDir = context.getStorage(incomingQueueBranch);
        return new IncomingCommandQueue(incomingQueueDir);
    }

    public AccessTokenServer getAccessToken(String serverUser, String tokenId) throws Exception {
        String userDataBranch = getUserDataBranch(serverUser);
        FejoaContext context = new FejoaContext(getServerUserDir(serverUser));
        StorageDir userDataDir = context.getStorage(userDataBranch);
        String accessStoreId = userDataDir.readString(UserData.ACCESS_STORE_KEY);
        StorageDir tokenDir = new StorageDir(context.getStorage(accessStoreId), tokenId);
        return new AccessTokenServer(context, tokenDir);
    }
}
