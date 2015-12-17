package org.fejoa.server;

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

    public IncomingCommandQueue getIncomingCommandQueue(String serverUser) throws Exception {
        JSONObject settings = getAccountSettings(serverUser).getSettings();
        if (!settings.has(CreateAccountJob.USER_DATA_BRANCH_KEY))
            throw new Exception("No user data branch set");
        String userDataBranch = settings.getString(CreateAccountJob.USER_DATA_BRANCH_KEY);
        FejoaContext context = new FejoaContext(getServerUserDir(serverUser));
        StorageDir userDataDir = context.getStorage(userDataBranch);
        String incomingQueueBranch = userDataDir.readString(UserData.IN_COMMAND_QUEUE_ID_KEY);
        StorageDir incomingQueueDir = context.getStorage(incomingQueueBranch);
        return new IncomingCommandQueue(incomingQueueDir);
    }
}
