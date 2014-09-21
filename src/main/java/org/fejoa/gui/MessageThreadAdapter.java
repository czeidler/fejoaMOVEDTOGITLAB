/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;


import org.fejoa.library.mailbox.Message;
import org.fejoa.library.mailbox.MessageBranch;
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library.support.WeakListenable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;


public class MessageThreadAdapter extends WeakListenable<ListDataListener> implements ListModel {
    final private MessageChannel messageChannel;

    final private MessageBranch.IListener threadListener = new MessageBranch.IListener() {
        @Override
        public void onMessageAdded(Message message) {
            notifyMessageAdded();
        }
    };

    public MessageThreadAdapter(MessageChannel messageChannel) {
        this.messageChannel = messageChannel;

        messageChannel.getBranch().addListener(threadListener);
    }

    @Override
    public void finalize() {
        messageChannel.getBranch().removeListener(threadListener);
    }

    @Override
    public int getSize() {
        return messageChannel.getBranch().getNumberOfMessages();
    }

    @Override
    public Object getElementAt(int i) {
        return messageChannel.getBranch().getMessage(i).getBody();
    }

    @Override
    public void addListDataListener(ListDataListener listDataListener) {
        addListener(listDataListener);
    }

    @Override
    public void removeListDataListener(ListDataListener listDataListener) {
        removeListener(listDataListener);
    }

    private void notifyMessageAdded() {
        int index = getSize();
        for (ListDataListener listener : getListeners())
            listener.intervalAdded(new ListDataEvent(this, ListDataEvent.INTERVAL_ADDED, index, index));
    }
}
