package org.fejoa.gui;

import org.fejoa.library.mailbox.Mailbox;
import org.fejoa.library.mailbox.MessageChannel;
import rx.Observer;

import javax.swing.*;


public class ThreadView {
    private JPanel mainPanel;
    private JList messageList;
    private JTextPane composeTextPane;
    private JButton sendButton;
    final private DefaultListModel loadingListModel = new DefaultListModel();

    private Mailbox.MessageChannelRef selectedMessageChannel;
    private MessageThreadAdapter threadAdapter;

    public ThreadView() {
        loadingListModel.add(0, "loading...");
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public void selectChannel(final Mailbox.MessageChannelRef messageChannelRef) {
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
                threadAdapter = new MessageThreadAdapter(channel);
                messageList.setModel(threadAdapter);
            }
        });

    }
}
