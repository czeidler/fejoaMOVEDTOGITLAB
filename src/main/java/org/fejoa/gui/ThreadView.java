/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.mailbox.Mailbox;
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library.mailbox.Messenger;
import rx.Observer;
import rx.concurrency.SwingScheduler;

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
    private Mailbox.MessageChannelRef selectedMessageChannel;
    private MessageChannel messageChannel;
    private MessageThreadAdapter threadAdapter;

    public ThreadView(Mailbox mailbox) {
        this.mailbox = mailbox;
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

        messageChannelRef.get(SwingScheduler.getInstance()).subscribe(new Observer<MessageChannel>() {
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
    }
}
