/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;


import org.apache.commons.io.IOUtils;
import org.fejoa.library.messages.PlainEnvelope;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PlainTextCommand implements ICommand {
    final private byte[] command;

    public PlainTextCommand(String command) throws JSONException, IOException {
        InputStream inputStream = PlainEnvelope.pack(new ByteArrayInputStream(command.getBytes()), true);
        this.command = IOUtils.toByteArray(inputStream);
    }

    @Override
    public byte[] getCommand() {
        return command;
    }
}
