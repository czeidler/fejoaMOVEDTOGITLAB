/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class BranchAccessRight {
    final static private String TYPE_KEY = "type";
    final static private String RIGHTS_KEY = "rights";

    final static public int CLOSED = 0x00;
    final static public int PULL = 0x01;
    final static public int PUSH = 0x02;
    final static public int PULL_PUSH = PULL | PUSH;

    static public class Entry {
        final private String branch;
        private int rights = CLOSED;

        public Entry(String branch, int rights) {
            this.branch = branch;
            this.rights = rights;
        }

        public Entry(JSONObject object) throws JSONException {
            branch = object.getString(Constants.BRANCH_KEY);
            rights = object.getInt(RIGHTS_KEY);
        }

        public JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put(Constants.BRANCH_KEY, branch);
            object.put(RIGHTS_KEY, rights);
            return object;
        }

        public String getBranch() {
            return branch;
        }

        public int getRights() {
            return rights;
        }

        public void setRights(int rights) {
            this.rights = rights;
        }
    }

    final static public String CONTACT_ACCESS = "contactAccess";
    final static public String MIGRATION_ACCESS = "migrationAccess";
    final private String type;
    final private List<Entry> entries = new ArrayList<>();

    public BranchAccessRight(String type) {
        this.type = type;
    }

    public BranchAccessRight(JSONObject object) throws JSONException {
        type = object.getString(TYPE_KEY);
        JSONArray accessRights = object.getJSONArray(RIGHTS_KEY);
        for (int i = 0; i < accessRights.length(); i++)
            entries.add(new Entry(accessRights.getJSONObject(i)));
    }

    public String getType() {
        return type;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put(TYPE_KEY, type);

        JSONArray accessRights = new JSONArray();
        for (Entry entry : entries)
            accessRights.put(entry.toJson());

        object.put(RIGHTS_KEY, accessRights);
        return object;
    }

    public void addBranchAccess(String branch, int rights) {
        entries.add(new Entry(branch, rights));
    }

    public List<Entry> getEntries() {
        return entries;
    }
}