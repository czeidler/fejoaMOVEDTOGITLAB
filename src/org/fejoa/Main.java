/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa;

import org.fejoa.gui.LoginScreen;
import org.fejoa.library.DatabaseBucket;
import org.fejoa.library.Profile;

import javax.swing.*;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

public class Main {

    public static void main(String[] args) {
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

        boolean opened = false;

        String password = "test";
        Profile profile = null;
        try {
            profile = new Profile(DatabaseBucket.get(".git", "profile"), "");

            while (!opened) {
                try {
                    opened = profile.open(password);
                } catch (IOException e) {
                    break;
                }
                if (!opened) {
                    // open password dialog
                }
            }

            if (!opened) {
                // create dialog
                profile.createNew(password);
               // profile.setAllRemotes();
                profile.commit();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        JFrame frame = new JFrame("LoginScreen");
        frame.setContentPane(new LoginScreen(profile).getContent());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
