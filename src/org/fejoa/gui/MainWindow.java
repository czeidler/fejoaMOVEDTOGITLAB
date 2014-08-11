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
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library.remote.HTMLRequest;
import org.fejoa.library.remote.RemoteConnection;
import org.fejoa.library.remote.RemoteStorageLink;
import org.fejoa.library.remote.ServerSync;
import rx.Observer;
import rx.concurrency.Schedulers;
import rx.concurrency.SwingScheduler;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;


public class MainWindow extends JDialog {
    private JPanel contentPane;
    private JLabel statusLabel;
    private JList messageChannelList;
    private JButton newMessageButton;
    private JPanel threadPanel;
    private JPanel messageCardPanel;
    private JPanel channelsPanel;
    private SendMessageFrame sendMessageObject;
    private ThreadView threadViewObject;
    final private Profile profile;

    // we need a strong reference
    final private MessageChannelAdapter messageChannelAdapter;

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    final String NEW_MESSAGE_CARD = "new message";
    final String THREAD_CARD = "thread";

    public MainWindow(Profile profile) {
        this.profile = profile;

        setContentPane(contentPane);
        setModal(true);

        threadViewObject = new ThreadView();
        messageCardPanel.add(threadViewObject.getPanel(), THREAD_CARD);

        messageChannelAdapter = new MessageChannelAdapter(profile.getMainMailbox());
        messageChannelList.setModel(messageChannelAdapter);

        messageChannelList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                showMessageThread(listSelectionEvent.getFirstIndex());
            }
        });

        sendMessageObject = new SendMessageFrame(profile.getMainMailbox());
        messageCardPanel.add(sendMessageObject.getPanel(), NEW_MESSAGE_CARD);
        ((CardLayout)messageCardPanel.getLayout()).show(messageCardPanel, NEW_MESSAGE_CARD);

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

    private void showMessageThread(final int i) {
        ((CardLayout)messageCardPanel.getLayout()).show(messageCardPanel, THREAD_CARD);

        threadViewObject.selectChannel(profile.getMainMailbox().getMessageChannel(i));
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
