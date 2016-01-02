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
import java.util.logging.Logger;


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
        String handlerName();

        /**
         * Handler for a command.
         *
         * @param command the command entry
         * @return null if unhandled
         * @throws Exception
         */
        ReturnValue handle(CommandQueue.Entry command) throws Exception;
    }

    final static private Logger LOG = Logger.getLogger(IncomingCommandManager.class.getName());
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

    public IncomingCommandManager(UserData userData) {
        this.queue = userData.getIncomingCommandQueue();

        addHandler(new ContactRequestHandler(userData));
        addHandler(new AccessCommandHandler(userData));
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
        boolean anyHandled = false;
        for (CommandQueue.Entry command : commands) {
            if (handleCommand(command)) {
                anyHandled = true;
                break;
            }
        }
        if (anyHandled)
            queue.commit();
    }

    private boolean handleCommand(CommandQueue.Entry command) {
        return handleCommand(command, handlerList, 0);
    }

    private boolean handleCommand(CommandQueue.Entry command, List<Handler> handlers, int retryCount) {
        if (retryCount > 1)
            return false;
        boolean handled = false;
        List<Handler> retryHandlers = new ArrayList<>();
        for (Handler handler : handlers) {
            ReturnValue returnValue = null;
            try {
                returnValue = handler.handle(command);
            } catch (Exception e) {
                LOG.warning("Exception in command: " + handler.handlerName());
                notifyOnException(e);
            }
            if (returnValue == null)
                continue;
            handled = true;
            if (returnValue.status == ReturnValue.HANDLED) {
                queue.removeCommand(command);
                notifyOnCommandReceived(returnValue);
            } else if (returnValue.status == ReturnValue.RETRY)
                retryHandlers.add(handler);
            break;
        }
        if (!handled)
            LOG.warning("Unhandled command!");

        retryCount++;
        if (retryHandlers.size() > 0)
            handleCommand(command, retryHandlers, retryCount);

        return handled;
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
