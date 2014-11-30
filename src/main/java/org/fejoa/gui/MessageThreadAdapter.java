/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;


import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.mailbox.Message;
import org.fejoa.library.mailbox.MessageBranch;
import org.fejoa.library.mailbox.MessageChannel;
import org.fejoa.library.support.WeakListenable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.io.IOException;


public class MessageThreadAdapter extends WeakListenable<ListDataListener> implements ListModel {
    final private MessageChannel messageChannel;
    final private MessageBranch messageBranch;

    final private MessageBranch.IListener threadListener = new MessageBranch.IListener() {
        @Override
        public void onMessageAdded(Message message) {
            notifyMessageAdded();
        }

        @Override
        public void onCommit() {

        }
    };

    public MessageThreadAdapter(MessageChannel messageChannel) {
        this.messageChannel = messageChannel;

        messageBranch = messageChannel.getBranch();
        messageBranch.addListener(threadListener);
    }

    @Override
    public void finalize() {
        messageBranch.removeListener(threadListener);
    }

    @Override
    public int getSize() {
        return messageBranch.getNumberOfMessages();
    }

    @Override
    public Object getElementAt(int i) {
        //Message message = messageChannel.getBranch().getMessage(i);
        return messageBranch.getMessage(i).getBody();
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
