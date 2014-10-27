package org.fejoa.library.mailbox;

import org.fejoa.library.SecureStorageDir;
import org.fejoa.library.StorageDir;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.DatabaseFactory;
import org.fejoa.library.database.IDatabaseInterface;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
import java.util.Map;


public class MailboxBookkeeping extends WeakListenable<MailboxBookkeeping.IListener> {
    public interface IListener {
        void onDirtyBranches();
    }

    private void notifyDirtyBranches() {
        for (IListener listener : getListeners())
            listener.onDirtyBranches();
    }

    final private Mailbox mailbox;
    final private Map<String, RemoteEntry> remoteEntryCache = new HashMap<>();
    final private List<RemoteEntry> dirtyRemotes = new ArrayList<>();
    final private SecureStorageDir baseDir;

    public MailboxBookkeeping(String path, Mailbox mailbox) throws IOException, CryptoException {
        SecureStorageDir security = mailbox.getStorageDir();
        SecureStorageDir root = SecureStorageDir.createStorage(path, "mailboxes", security.getKeyStore(),
                security.getKeyId());
        baseDir = new SecureStorageDir(root, mailbox.getUid());

        this.mailbox = mailbox;
    }

    public List<RemoteEntry> getDirtyRemotes() {
        return dirtyRemotes;
    }

    public class RemoteEntry {
        final private SecureStorageDir remoteDir;
        final private SecureStorageDir dirtyDir;
        final private List<String> dirtyBranches;

        public RemoteEntry(String remote) throws IOException {
            SecureStorageDir dir = new SecureStorageDir(baseDir, new String(CryptoHelper.sha1Hash(remote.getBytes())));
            remoteDir = new SecureStorageDir(dir, "remoteStatus");
            dirtyDir = new SecureStorageDir(dir, "dirtyBranches");

            dirtyBranches = readDirtyBranches();
        }

        public void markAsDirty(String branch) throws IOException {
            dirtyDir.writeString(branch, branch);
            if (!dirtyRemotes.contains(this))
                dirtyRemotes.add(this);
            if (!dirtyBranches.contains(branch))
                dirtyBranches.add(branch);
        }

        private void cleanDirtyBranch(String branch) throws IOException {
            dirtyDir.remove(branch);

            dirtyBranches.remove(branch);
            if (dirtyBranches.size() == 0)
                dirtyRemotes.remove(this);
        }

        public List<String> getDirtyBranches() {
            return dirtyBranches;
        }

        private List<String> readDirtyBranches() {
            try {
                return dirtyDir.listFiles("");
            } catch (IOException e) {
                return new ArrayList<>();
            }
        }

        public void updateRemoteStatus(String branch, String remoteTip) throws IOException {
            String localTip = getLocalTip(branch);
            if (localTip.equals(remoteTip))
                cleanDirtyBranch(branch);

            remoteDir.writeString(branch, remoteTip);
        }

        private String getLocalTip(String branch) throws IOException {
            IDatabaseInterface database = DatabaseFactory.getDatabaseFor(
                    mailbox.getStorageDir().getDatabase().getPath(), branch);
            return database.getTip();
        }
    }

    public RemoteEntry edit(String remote) throws IOException {
        if (remoteEntryCache.containsKey(remote))
            return remoteEntryCache.get(remote);
        RemoteEntry entry = new RemoteEntry(remote);
        remoteEntryCache.put(remote, entry);
        return entry;
    }


    /**
     * Commits the bookkeeping dir and write the sync status with the mailbox tip.
     *
     * @param mailboxTip
     * @throws java.io.IOException
     */
    public void commit(String mailboxTip) throws IOException {
        baseDir.writeString("mailboxTip", mailboxTip);
        baseDir.commit();

        if (dirtyRemotes.size() > 0)
            notifyDirtyBranches();
    }
}
