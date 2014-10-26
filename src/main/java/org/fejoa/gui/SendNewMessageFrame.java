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
import org.fejoa.library.mailbox.*;
import org.fejoa.library.remote.RemoteConnectionJob;
import rx.Observer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class SendNewMessageFrame {
    private JPanel mainPanel;
    private JButton sendButton;
    private JTextField receiverTextField;
    private JTextPane messagePane;
    private JTextField subjectTextField;

    final private Mailbox mailbox;
    final private INotifications notifications;

    public SendNewMessageFrame(Mailbox mailbox, INotifications notifications) {
        this.mailbox = mailbox;
        this.notifications = notifications;

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    sendMessage();
                } catch (CryptoException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    private void sendMessage() throws CryptoException, IOException {
        String subject = subjectTextField.getText();
        String receiver = receiverTextField.getText();
        List<String> receivers = new ArrayList<>();
        receivers.add(receiver);
        String body = messagePane.getText();

        Messenger messenger = new Messenger(mailbox);
        MessageChannel messageChannel = messenger.createNewThreadMessage(subject, receivers, body);
        messenger.sendMessageChannel(messageChannel, notifications);
    }
}
