package org.fejoa.server;

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

    public String serverUserDir(String serverUser) {
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
}
