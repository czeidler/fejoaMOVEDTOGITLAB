/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.command;


public class PlainTextCommand implements ICommand {
    final private String command;

    public PlainTextCommand(String command) {
        this.command = command;
    }

    @Override
    public byte[] getCommand() {
        return command.getBytes();
    }
}
