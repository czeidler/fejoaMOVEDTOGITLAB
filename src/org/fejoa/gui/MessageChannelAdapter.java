/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.mailbox.Mailbox;
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library.support.WeakListenable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;


public class MessageChannelAdapter extends WeakListenable<ListDataListener> implements ListModel {
    private Mailbox mailbox;
    private Mailbox.Listener mailboxListener = new Mailbox.Listener() {
        @Override
        public void onMessageChannelAdded(Mailbox.MessageChannelRef channelRef) {
            notifyMessageChannelAdded();
        }
    };

    public MessageChannelAdapter(Mailbox mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public int getSize() {
        return mailbox.getNumberOfMessageChannels();
    }

    @Override
    public Object getElementAt(int index) {
        Mailbox.MessageChannelRef ref =  mailbox.getMessageChannel(index);
        try {
            MessageChannel channel = ref.getSync();
            return channel.getBranch();
        } catch (Exception e) {
            return "Failed to load channel!";
        }
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
}
