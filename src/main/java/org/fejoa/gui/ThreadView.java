/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.INotifications;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.mailbox.Mailbox;
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library.mailbox.Messenger;
import org.fejoa.library.remote.RemoteConnectionJob;
import rx.Observer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;


public class ThreadView {
    private JPanel mainPanel;
    private JList messageList;
    private JTextPane composeTextPane;
    private JButton sendButton;
    final private DefaultListModel loadingListModel = new DefaultListModel();

    final private Mailbox mailbox;
    final private INotifications notifications;
    private Mailbox.MessageChannelRef selectedMessageChannel;
    private MessageChannel messageChannel;
    private MessageThreadAdapter threadAdapter;

    public ThreadView(Mailbox mailbox, INotifications notifications) {
        this.mailbox = mailbox;
        this.notifications = notifications;
        loadingListModel.add(0, "loading...");

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    sendMessage();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public void selectChannel(final Mailbox.MessageChannelRef messageChannelRef) {
        messageChannel = null;

        sendButton.setEnabled(false);
        selectedMessageChannel = messageChannelRef;
        messageList.setModel(loadingListModel);

        messageChannelRef.get().subscribe(new Observer<MessageChannel>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                System.out.println(e.getMessage());
            }

            @Override
            public void onNext(MessageChannel channel) {
                if (selectedMessageChannel != messageChannelRef)
                    return;
                messageChannel = channel;
                threadAdapter = new MessageThreadAdapter(channel);
                messageList.setModel(threadAdapter);
                sendButton.setEnabled(true);
            }
        });
    }

    private void sendMessage() throws IOException, CryptoException {
        String body = composeTextPane.getText();

        Messenger messenger = new Messenger(mailbox);
        messenger.addMessageToThread(messageChannel, body);
        messenger.publishMessageChannel(messageChannel).subscribe(new Observer<RemoteConnectionJob.Result>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                notifications.error(e.getMessage());
            }

            @Override
            public void onNext(RemoteConnectionJob.Result result) {
                if (result.status < RemoteConnectionJob.Result.DONE)
                    notifications.error(result.message);
                else
                    notifications.info(result.message);
            }
        });
    }
}
