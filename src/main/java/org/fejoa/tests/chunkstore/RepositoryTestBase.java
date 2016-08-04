/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.chunkstore;

import junit.framework.TestCase;
import org.apache.commons.codec.binary.Base64;
import org.fejoa.chunkstore.BoxPointer;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StorageLib;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class RepositoryTestBase extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    static class TestFile {
        TestFile(String content) {
            this.content = content;
        }

        String content;
        BoxPointer boxPointer;
    }

    static class TestDirectory {
        Map<String, TestFile> files = new HashMap<>();
        Map<String, TestDirectory> dirs = new HashMap<>();
        BoxPointer boxPointer;
    }

    static class TestCommit {
        String message;
        TestDirectory directory;
        BoxPointer boxPointer;
    }

    class DatabaseStingEntry {
        public String path;
        public String content;

        public DatabaseStingEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    Repository.ICommitCallback simpleCommitCallback = new Repository.ICommitCallback() {
        static final String DATA_HASH_KEY = "dataHash";
        static final String BOX_HASH_KEY = "boxHash";

        @Override
        public String commitPointerToLog(BoxPointer commitPointer) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(DATA_HASH_KEY, commitPointer.getDataHash().toHex());
                jsonObject.put(BOX_HASH_KEY, commitPointer.getBoxHash().toHex());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            String jsonString = jsonObject.toString();
            return Base64.encodeBase64String(jsonString.getBytes());
        }

        @Override
        public BoxPointer commitPointerFromLog(String logEntry) {
            String jsonString = new String(Base64.decodeBase64(logEntry));
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                return new BoxPointer(HashValue.fromHex(jsonObject.getString(DATA_HASH_KEY)),
                        HashValue.fromHex(jsonObject.getString(BOX_HASH_KEY)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public byte[] createCommitMessage(String message, BoxPointer rootTree, Collection<BoxPointer> parents) {
            return message.getBytes();
        }
    };

    protected void add(Repository database, List<DatabaseStingEntry> content, DatabaseStingEntry entry)
            throws Exception {
        content.add(entry);
        database.writeBytes(entry.path, entry.content.getBytes());
    }

    protected void containsContent(Repository database, List<DatabaseStingEntry> content) throws IOException,
            CryptoException {
        for (DatabaseStingEntry entry : content) {
            byte bytes[] = database.readBytes(entry.path);
            assertTrue(entry.content.equals(new String(bytes)));
        }
    }
}
