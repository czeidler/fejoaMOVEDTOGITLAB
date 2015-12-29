/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library2.database.StorageDir;

import java.io.IOException;


public class AccessContact implements IStorageDirBundle {
    final static private String CONTACT_ID_KEY = "contact";
    final static private String ACKNOWLEDGED_ENTRY_KEY = "acknowledgedAccess";
    private String contact;
    // hash of the acknowledged access entry hash
    private String acknowledgedAccess = "";

    public AccessContact(String contactId) {
        this.contact = contactId;
    }

    public AccessContact() {

    }

    public String getContact() {
        return contact;
    }

    @Override
    public void write(StorageDir dir) throws IOException {
        dir.writeString(CONTACT_ID_KEY, contact);
        dir.writeString(ACKNOWLEDGED_ENTRY_KEY, acknowledgedAccess);
    }

    @Override
    public void read(StorageDir dir) throws IOException {
        contact = dir.readString(CONTACT_ID_KEY);
        acknowledgedAccess = dir.readString(ACKNOWLEDGED_ENTRY_KEY);
    }
}
