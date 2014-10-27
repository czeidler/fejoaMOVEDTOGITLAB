/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.INotifications;
import org.fejoa.library.Profile;
import org.fejoa.library.mailbox.Mailbox;
import org.fejoa.library.remote.*;
import rx.Observer;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;


class GuiNotifications implements INotifications {
    final private INotifications child;

    public GuiNotifications(INotifications child) {
        this.child = child;
    }

    @Override
    public void info(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                child.info(message);
            }
        });
    }

    @Override
    public void error(final String error) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                child.error(error);
            }
        });
    }
}

class LabelNotifications implements INotifications {
    final private JLabel statusLabel;

    public LabelNotifications(JLabel statusLabel) {
        this.statusLabel = statusLabel;
    }

    @Override
    public void info(String message) {
        statusLabel.setText("Info: " + message);
    }

    @Override
    public void error(String error) {
        statusLabel.setText("Error: " + error);
    }
}

public class MainWindow extends JDialog {
    private JPanel contentPane;
    private JLabel statusLabel;
    private JList messageChannelList;
    private JButton newMessageButton;
    private JPanel threadPanel;
    private JPanel messageCardPanel;
    private JPanel channelsPanel;

    final private LabelNotifications notifications;
    private SendNewMessageFrame sendMessageObject;
    private ThreadView threadViewObject;
    final private Profile profile;

    // we need a strong reference
    final private MessageChannelAdapter messageChannelAdapter;

    final String NEW_MESSAGE_CARD = "new message";
    final String THREAD_CARD = "thread";

    public MainWindow(Profile profile) {
        this.profile = profile;

        notifications = new LabelNotifications(statusLabel);

        setContentPane(contentPane);
        setModal(true);

        Mailbox mailbox = profile.getMainMailbox();
        threadViewObject = new ThreadView(mailbox);
        messageCardPanel.add(threadViewObject.getPanel(), THREAD_CARD);

        messageChannelAdapter = new MessageChannelAdapter(mailbox);
        messageChannelList.setModel(messageChannelAdapter);

        messageChannelList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                if (listSelectionEvent.getValueIsAdjusting())
                    return;
                showMessageThread(messageChannelList.getSelectedIndex());
            }
        });

        sendMessageObject = new SendNewMessageFrame(mailbox);
        messageCardPanel.add(sendMessageObject.getPanel(), NEW_MESSAGE_CARD);
        ((CardLayout)messageCardPanel.getLayout()).show(messageCardPanel, NEW_MESSAGE_CARD);

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                ConnectionManager.get().shutdown(true);
                dispose();
            }

            public void windowOpened(WindowEvent e) {
                start();
            }
        });

        newMessageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                showNewMessage();
            }
        });
    }

    public INotifications getGuiNotifications() {
        return new GuiNotifications(notifications);
    }

    private void showNewMessage() {
        ((CardLayout)messageCardPanel.getLayout()).show(messageCardPanel, NEW_MESSAGE_CARD);
    }

    private void showMessageThread(final int i) {
        ((CardLayout)messageCardPanel.getLayout()).show(messageCardPanel, THREAD_CARD);

        threadViewObject.selectChannel(profile.getMainMailbox().getMessageChannel(i));
    }

    private void start() {
        notifications.info("start watching");

        List<RemoteStorageLink> links = new ArrayList<>(profile.getRemoteStorageLinks().values());

        ConnectionManager.get().startWatching(links, new ServerWatcher.IListener() {
            @Override
            public void onBranchesUpdated(List<RemoteStorageLink> links) {
                for (RemoteStorageLink link : links)
                    syncBranch(link);
            }

            @Override
            public void onError(String message) {
                notifications.error("WatchListener: " + message);
            }

            @Override
            public void onResult(RemoteConnectionJob.Result args) {
                notifications.info("WatchListener: " + args.message);
            }
        });

        profile.getMainMailbox().startSyncing(notifications);
    }

    private void syncBranch(RemoteStorageLink remoteStorageLink) {
        final String branch = remoteStorageLink.getDatabaseInterface().getBranch();
        ServerSync serverSync = new ServerSync(remoteStorageLink);
        serverSync.sync().subscribe(new Observer<RemoteConnectionJob.Result>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                notifications.error("sync error");
            }

            @Override
            public void onNext(RemoteConnectionJob.Result result) {
                if (result.status == RemoteConnectionJob.Result.DONE)
                    notifications.info(branch + ": sync ok");
                else
                    notifications.error(branch + ": sync failed");
            }
        });
    }
}

