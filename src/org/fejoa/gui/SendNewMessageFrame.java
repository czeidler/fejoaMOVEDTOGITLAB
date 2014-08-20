package org.fejoa.gui;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.mailbox.*;

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

    public SendNewMessageFrame(Mailbox mailbox) {
        this.mailbox = mailbox;

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

        new Messenger(mailbox).createNewThreadMessage(subject, receivers, body);
    }
}
