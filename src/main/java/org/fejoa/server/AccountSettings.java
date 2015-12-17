/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.Scanner;


public class AccountSettings {
    static final public String ACCOUNT_INFO_FILE = "account.settings";

    final private String dir;

    public AccountSettings(String dir) {
        this.dir = dir;
    }

    private File getSettingsFile() {
        return new File(dir, ACCOUNT_INFO_FILE);
    }

    public JSONObject getSettings() throws FileNotFoundException, JSONException {
        File settingsFile = getSettingsFile();
        if (settingsFile.exists()) {
            String content = new Scanner(settingsFile).useDelimiter("\\Z").next();
            return new JSONObject(content);
        }
        return new JSONObject();
    }

    public void update(JSONObject update, JSONObject remove) throws IOException,
            JSONException {
        JSONObject settings = getSettings();

        for (String key : JSONObject.getNames(update))
            settings.put(key, update.get(key));

        if (remove != null) {
            for (String key : JSONObject.getNames(remove))
                settings.remove(key);
        }

        writeSettings(settings);
    }

    private void writeSettings(JSONObject params) throws IOException {
        File accountInfoFile = getSettingsFile();

        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(accountInfoFile)));
        writer.write(params.toString());
        writer.flush();
        writer.close();
    }
}
