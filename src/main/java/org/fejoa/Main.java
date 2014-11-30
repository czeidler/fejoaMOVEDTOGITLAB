/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa;

import org.fejoa.gui.CreateProfileDialog;
import org.fejoa.gui.MainWindow;
import org.fejoa.gui.PasswordDialog;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.database.SecureStorageDirBucket;
import org.fejoa.library.Profile;
import org.fejoa.library.crypto.CryptoException;

import javax.swing.*;
import java.io.*;


class GeneratingProfileDialog extends JDialog{
    public GeneratingProfileDialog() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        setContentPane(new JLabel("Generating Profile. This could take a while..."));
        setModal(false);
        pack();
    }
}

public class Main {

    static private String readPassword() {
        FileInputStream fileInputStream;
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

    private static void setupLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
        }
    }

    public static void main(String[] args) {
        setupLookAndFeel();

        //CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

        boolean opened = false;

        // convenient hack
        String password = readPassword();

        Profile profile = null;

        try {
            profile = new Profile(SecureStorageDirBucket.getDefault("profile"), "");
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
                passwordDialog.setLocationRelativeTo(null);
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
            createProfileDialog.setLocationRelativeTo(null);
            createProfileDialog.setVisible(true);
            int option = createProfileDialog.getResult();

            if (option == JOptionPane.OK_OPTION) {
                try {
                    String userName = createProfileDialog.getUserName();
                    password = createProfileDialog.getPassword();
                    String server = createProfileDialog.getServerName();

                    GeneratingProfileDialog generatingProfileDialog = new GeneratingProfileDialog();
                    generatingProfileDialog.setVisible(true);

                    profile.createNew(password);

                    generatingProfileDialog.dispose();

                    ContactPrivate myself = profile.getMainUserIdentity().getMyself();
                    myself.setServerUser(userName);
                    myself.setServer(server);
                    myself.write();

                    profile.setEmptyRemotes(server, userName, myself);
                    profile.commit();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.exit(-1);
                }

            } else
                System.exit(0);
        }

        MainWindow mainWindow = new MainWindow(profile);
        mainWindow.pack();
        mainWindow.setLocationRelativeTo(null);
        mainWindow.setVisible(true);
    }
}
