/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 * TODO fix concurrency and maybe integrate it more tight into the chunk store.
 */
public class ChunkStoreBranchLog {
    static public class Entry {
        HashValue tip;
        final List<HashValue> changes = new ArrayList<>();

        public HashValue getTip() {
            return tip;
        }
    }

    final private File logfile;
    final private List<Entry> entries = new ArrayList<>();

    public ChunkStoreBranchLog(File logfile) throws IOException {
        this.logfile = logfile;

        read();
    }

    private void read() throws IOException {
        BufferedReader reader;
        try {
            FileInputStream fileInputStream = new FileInputStream(logfile);
            reader = new BufferedReader(new InputStreamReader(fileInputStream));
        } catch (FileNotFoundException e) {
            return;
        }
        String line;
        while ((line = reader.readLine()) != null && line.length() != 0) {
            Entry entry = new Entry();
            entry.tip = HashValue.fromHex(line);
            int nChanges = Integer.parseInt(reader.readLine());
            for (int i = 0; i < nChanges; i++) {
                entry.changes.add(HashValue.fromHex(reader.readLine()));
            }
            entries.add(entry);
        }
    }

    public Entry getTip() {
        if (entries.size() == 0)
            return null;
        return entries.get(entries.size() - 1);
    }

    public void add(HashValue newTip, List<HashValue> changes) throws IOException {
        Entry entry = new Entry();
        entry.tip = newTip;
        entry.changes.addAll(changes);
        write(entry);
        entries.add(entry);
    }

    private void write(Entry entry) throws IOException {
        if(!logfile.exists()) {
            logfile.getParentFile().mkdirs();
            logfile.createNewFile();
        }

        FileOutputStream outputStream = new FileOutputStream(logfile, false);
        try {
            outputStream.write((entry.tip.toHex() + "\n").getBytes());
            outputStream.write(("" + entry.changes.size() + "\n").getBytes());
            for (HashValue change : entry.changes)
                outputStream.write((change.toHex() + "\n").getBytes());
        } finally {
            outputStream.close();
        }
    }
}
