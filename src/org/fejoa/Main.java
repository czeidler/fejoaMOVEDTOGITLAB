/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa;

import org.fejoa.gui.CreateProfileDialog;
import org.fejoa.gui.LoginScreen;
import org.fejoa.gui.PasswordDialog;
import org.fejoa.library.SecureStorageDirBucket;
import org.fejoa.library.Profile;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.SyncManager;

import javax.swing.*;
import java.io.*;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

public class Main {

    static private String readPassword() {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream("password");
        } catch (FileNotFoundException e) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
        try {
            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void main(String[] args) {
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

        boolean opened = false;

        // convenient hack
        String password = readPassword();

        Profile profile = null;

        try {
            profile = new Profile(SecureStorageDirBucket.get(".git", "profile"), "");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        while (!opened) {
            try {
                opened = profile.open(password);
            } catch (IOException e) {
                break;
            } catch (CryptoException e) {
                e.printStackTrace();
            }
            if (!opened) {
                // open password dialog
                PasswordDialog passwordDialog = new PasswordDialog();
                passwordDialog.pack();
                passwordDialog.setVisible(true);

                int option = passwordDialog.getResult();
                if (option == JOptionPane.OK_OPTION)
                    password = passwordDialog.getPassword();
                else
                    System.exit(0);
            }
        }

        if (!opened) {
            // create dialog

            CreateProfileDialog createProfileDialog = new CreateProfileDialog();
            createProfileDialog.pack();
            createProfileDialog.setVisible(true);
            int option = createProfileDialog.getResult();

            if (option == JOptionPane.OK_OPTION) {

                try {
                    String userName = createProfileDialog.getUserName();
                    password = createProfileDialog.getPassword();
                    String fullServerAddress = "http://";
                    fullServerAddress += createProfileDialog.getServerName();
                    fullServerAddress += "/php_server/portal.php";

                    profile.createNew(password);
                    profile.setEmptyRemotes(fullServerAddress, userName);
                    profile.commit();
                } catch (Exception e) {
                    System.exit(-1);
                }

            } else
                System.exit(0);
        }
    }
}
