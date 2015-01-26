/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.mailbox.Mailbox;
import org.fejoa.library.mailbox.MessageBranch;
import org.fejoa.library.mailbox.MessageBranchInfo;
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library.support.WeakListenable;
import rx.Observer;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.List;


public class MessageChannelAdapter extends WeakListenable<ListDataListener> implements ListModel {
    private Mailbox mailbox;
    private Mailbox.Listener mailboxListener = new Mailbox.Listener() {
        @Override
        public void onMessageChannelAdded(Mailbox.MessageChannelRef channelRef) {
            notifyMessageChannelAdded();
        }
    };
    private List<String> labels = new ArrayList<>();

    public MessageChannelAdapter(Mailbox mailbox) {
        this.mailbox = mailbox;
        this.mailbox.addListener(mailboxListener);
    }

    @Override
    public void finalize() {
        mailbox.removeListener(mailboxListener);
    }

    @Override
    public int getSize() {
        return mailbox.getNumberOfMessageChannels();
    }

    private void setLabel(int index, String label) {
        if (labels.size() <= index)
            labels.add(index, label);
        else
            labels.set(index, label);
    }

    private String getLabel(int index) {
        if (labels.size() <= index)
            return null;
        return labels.get(index);
    }

    @Override
    public Object getElementAt(final int index) {
        // The channel is fetched async, to make sure that the entry stays valid cache it for one
        // cycle.
        String cachedLabel = getLabel(index);
        if (cachedLabel != null)
            return cachedLabel;

        Mailbox.MessageChannelRef ref =  mailbox.getMessageChannel(index);
        final String[] label = {""};
        ref.get().subscribe(new Observer<MessageChannel>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(MessageChannel args) {
                label[0] = makeString(args);
                setLabel(index, label[0]);
                notifyMessageChannelLoaded(index);
            }
        });
        if (label[0].equals(""))
            label[0] = "loading...";
        return label[0];
    }

    private String makeString(MessageChannel channel) {
        MessageBranch branch = channel.getBranch();
        String subject = branch.getMessageBranchInfo().getSubject();
        if (subject.equals(""))
            subject = "no subject";
        String participants = "";
        for (MessageBranchInfo.Participant participant : branch.getMessageBranchInfo().getParticipants()) {
            if (!participants.equals(""))
                participants += ", ";
            participants += participant.address;
        }
        return subject + " (" + participants + ")";
    }

    @Override
    public void addListDataListener(ListDataListener listDataListener) {
        addListener(listDataListener);
    }

    @Override
    public void removeListDataListener(ListDataListener listDataListener) {
        removeListener(listDataListener);
    }

    private void notifyMessageChannelAdded() {
        int index = getSize();
        for (ListDataListener listener : getListeners())
            listener.intervalAdded(new ListDataEvent(this, ListDataEvent.INTERVAL_ADDED, index, index));
    }

    private void notifyMessageChannelLoaded(int index) {
        for (ListDataListener listener : getListeners())
            listener.intervalAdded(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, index, index));
    }
}
