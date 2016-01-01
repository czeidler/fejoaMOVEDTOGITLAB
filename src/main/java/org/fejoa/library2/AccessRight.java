/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.json.JSONException;
import org.json.JSONObject;


public class AccessRight {
    final static public int CLOSED = 0x00;
    final static public int PULL = 0x01;
    final static public int PUSH = 0x02;

    final static private String GIT_ACCESS_RIGHTS_KEY = "gitAccessRights";

    final private String branch;
    private int gitAccessRights = CLOSED;

    public AccessRight(String branch) {
        this.branch = branch;
    }

    public AccessRight(JSONObject object) throws JSONException {
        branch = object.getString(Constants.BRANCH_KEY);
        gitAccessRights = object.getInt(GIT_ACCESS_RIGHTS_KEY);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put(Constants.BRANCH_KEY, branch);
        object.put(GIT_ACCESS_RIGHTS_KEY, gitAccessRights);
        return object;
    }

    public String getBranch() {
        return branch;
    }

    public int getGitAccessRights() {
        return gitAccessRights;
    }

    public void setGitAccessRights(int gitAccessRights) {
        this.gitAccessRights = gitAccessRights;
    }
}
