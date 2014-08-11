package org.fejoa.gui;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.mailbox.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;


public class SendMessageFrame {
    private JPanel mainPanel;
    private JButton sendButton;
    private JTextField receiverTextField;
    private JTextPane messagePane;
    private JTextField subjectTextField;

    final private Mailbox mailbox;

    public SendMessageFrame(Mailbox mailbox) {
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
        String body = messagePane.getText();

        MessageBranchInfo branchInfo = new MessageBranchInfo();
        branchInfo.setSubject(subject);
        branchInfo.addParticipant(receiver, "");

        Message message = new Message();
        message.setBody(body);

        MessageChannel messageChannel = mailbox.createNewMessageChannel();
        MessageBranch messageBranch = messageChannel.getBranch();
        messageBranch.setMessageBranchInfo(branchInfo);
        messageBranch.addMessage(message);

        mailbox.addMessageChannel(messageChannel);
        mailbox.commit();
    }
}
