/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.Profile;
import org.fejoa.library.remote.HTMLRequest;
import org.fejoa.library.remote.RemoteConnection;
import org.fejoa.library.remote.RemoteStorageLink;
import org.fejoa.library.remote.ServerSync;
import rx.Observer;
import rx.concurrency.Schedulers;
import rx.concurrency.SwingScheduler;

import javax.swing.*;
import java.awt.event.*;


public class MainWindow extends JDialog {
    private JPanel contentPane;
    private JList list1;
    private JButton newMessageButton;
    private JLabel statusLabel;
    final private Profile profile;

    public MainWindow(Profile profile) {
        this.profile = profile;

        setContentPane(contentPane);
        setModal(true);


        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }

            public void windowOpened(WindowEvent e) {
                start();
            }
        });
    }

    private void start() {
        statusLabel.setText("start auth");

        ContactPrivate myself = profile.getUserIdentityList().get(0).getMyself();
        RemoteConnection remoteConnection = new RemoteConnection(new HTMLRequest("http://localhost/php_server/portal.php"));
        remoteConnection.requestAuthentication(myself, "lec")
                .subscribeOn(Schedulers.threadPoolForIO())
                .observeOn(SwingScheduler.getInstance())
                .subscribe(new Observer<Boolean>() {
                    boolean connected = false;
                    @Override
                    public void onCompleted() {
                        statusLabel.setText("auth done: " + connected);
                        if (connected)
                            startSync();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        statusLabel.setText("auth failed");
                    }

                    @Override
                    public void onNext(Boolean connected) {
                        this.connected = connected;
                        if (connected)
                            statusLabel.setText("auth ok");
                        else
                            statusLabel.setText("auth failed");
                    }
                });
    }

    private void startSync() {
        statusLabel.setText("start sync");

        RemoteStorageLink link = profile.getRemoteStorageLinks().values().iterator().next();
        ServerSync serverSync = new ServerSync(link);
        serverSync.sync()
                .subscribeOn(Schedulers.threadPoolForIO())
                .observeOn(SwingScheduler.getInstance())
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable throwable) {
                        statusLabel.setText("sync error");
                    }

                    @Override
                    public void onNext(Boolean connected) {
                        if (connected)
                            statusLabel.setText("sync ok");
                        else
                            statusLabel.setText("sync failed");
                    }
                });
    }

}
