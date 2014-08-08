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
import java.awt.*;
import java.awt.event.*;


public class MainWindow extends JDialog {
    private JPanel contentPane;
    private JLabel statusLabel;
    private JList messageChannelList;
    private JButton newMessageButton;
    private JButton sendButton;
    private JPanel threadPanel;
    private JPanel messageCardPanel;
    private JPanel channelsPanel;
    private SendMessageFrame sendMessageObject;
    final private Profile profile;

    private void setupSendMessage() {
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {

            }
        });
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    public MainWindow(Profile profile) {
        this.profile = profile;

        setContentPane(contentPane);
        setModal(true);

        messageChannelList.setModel(new MessageChannelAdapter(profile.getMainMailbox()));

        sendMessageObject = new SendMessageFrame();
        messageCardPanel.add(sendMessageObject.getPanel(), "new message");

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

        setupSendMessage();
    }

    private void start() {
        setStatus("start auth");

        ContactPrivate myself = profile.getUserIdentityList().get(0).getMyself();
        String server = myself.getServer();
        String fullAddress = "http://" + server + "/php_server/portal.php";

        RemoteConnection remoteConnection = new RemoteConnection(new HTMLRequest(fullAddress));
        remoteConnection.requestAuthentication(myself, "lec")
                .subscribeOn(Schedulers.threadPoolForIO())
                .observeOn(SwingScheduler.getInstance())
                .subscribe(new Observer<Boolean>() {
                    boolean connected = false;
                    @Override
                    public void onCompleted() {
                        setStatus("auth done: " + connected);
                        if (connected)
                            startSync();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        setStatus("auth failed");
                    }

                    @Override
                    public void onNext(Boolean connected) {
                        this.connected = connected;
                        if (connected)
                            setStatus("auth ok");
                        else
                            setStatus("auth failed");
                    }
                });
    }

    private void startSync() {
        setStatus("start sync");

        final RemoteStorageLink link = profile.getRemoteStorageLinks().values().iterator().next();
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
                        setStatus("sync error");
                    }

                    @Override
                    public void onNext(Boolean connected) {
                        if (connected)
                            setStatus("sync ok: " + link.getDatabaseInterface().getBranch().toString());
                        else
                            setStatus("sync failed");
                    }
                });
    }

}
