/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.support.WeakListenable;
import org.fejoa.library2.FejoaContext;
import org.fejoa.library2.UserData;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class IncomingCommandManager extends WeakListenable<IncomingCommandManager.IListener> {
    static public class ReturnValue {
        final static int HANDLED = 0;
        final static int RETRY = 1;

        final public int status;
        final public String command;

        public ReturnValue(int status, String command) {
            this.status = status;
            this.command = command;
        }
    }

    public interface IListener {
        void onCommandReceived(ReturnValue returnValue);
        void onException(Exception exception);
    }

    public interface Handler {
        ReturnValue handle(CommandQueue.Entry command) throws Exception;
    }

    final private IncomingCommandQueue queue;
    final private StorageDir.IListener storageListener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff, String base, String tip) {
            try {
                onNewCommands();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    final private List<Handler> handlerList = new ArrayList<>();

    public IncomingCommandManager(FejoaContext context, UserData userData) {
        this.queue = userData.getIncomingCommandQueue();

        addHandler(new ContactRequestHandler(context, userData.getContactStore()));
    }

    public void addHandler(Handler handler) {
        handlerList.add(handler);
    }

    public void start() {
        StorageDir dir = queue.getStorageDir();
        dir.addListener(storageListener);
    }

    private void onNewCommands() throws IOException {
        List<CommandQueue.Entry> commands = queue.getCommands();
        for (CommandQueue.Entry command : commands)
            handleCommand(command);
    }

    private void handleCommand(CommandQueue.Entry command) {
        handleCommand(command, handlerList, 0);
    }

    private void handleCommand(CommandQueue.Entry command, List<Handler> handlers, int retryCount) {
        if (retryCount > 1)
            return;
        List<Handler> retryHandlers = new ArrayList<>();
        for (Handler handler : handlers) {
            ReturnValue returnValue = null;
            try {
                returnValue = handler.handle(command);
            } catch (Exception e) {
                notifyOnException(e);
            }
            if (returnValue.status == ReturnValue.HANDLED) {
                queue.removeCommand(command);
                notifyOnCommandReceived(returnValue);
            } else if (returnValue.status == ReturnValue.RETRY)
                retryHandlers.add(handler);
        }
        if (retryHandlers.size() > 0)
            handleCommand(command, retryHandlers, retryCount++);
    }

    public void notifyOnCommandReceived(ReturnValue returnValue) {
        for (IListener listener : getListeners())
            listener.onCommandReceived(returnValue);
    }

    private void notifyOnException(Exception exception) {
        for (IListener listener : getListeners())
            listener.onException(exception);
    }
}
