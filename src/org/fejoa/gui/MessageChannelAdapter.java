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
import rx.Observer;

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

    @Override
    public Object getElementAt(final int index) {
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
                if (!label[0].equals(""))
                    notifyMessageChannelLoaded(index);
                else
                    label[0] = args.getBranchName();
            }
        });
        if (label[0].equals(""))
            label[0] = "loading...";
        return label[0];
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
