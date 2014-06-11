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

public class Main {

    public static void main(String[] args) {
        boolean opened = false;

        String password = "test";
        try {
            Profile profile = new Profile(DatabaseBucket.get(".git", "profile"), "");

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
                profile.commit();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        JFrame frame = new JFrame("LoginScreen");
        frame.setContentPane(new LoginScreen().getContent());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
