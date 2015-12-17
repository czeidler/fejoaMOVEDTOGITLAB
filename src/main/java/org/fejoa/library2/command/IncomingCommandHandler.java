/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;

import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class IncomingCommandHandler {
    public interface Handler {
        boolean handle(CommandQueue.Entry command);
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

    public IncomingCommandHandler(IncomingCommandQueue queue) {
        this.queue = queue;
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
        for (Handler handler : handlerList) {
            if (handler.handle(command)) {
                queue.removeCommand(command);
            }
        }
    }
}
