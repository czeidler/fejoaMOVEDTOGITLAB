package org.fejoa.library.mailbox;

import org.fejoa.library.SecureStorageDir;
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
    // remote name -> DirtyRemote
    final private Map<String, DirtyRemote> dirtyRemotes = new HashMap<>();
    final private SecureStorageDir baseDir;
    final private SecureStorageDir dirtyDir;

    public MailboxBookkeeping(String path, Mailbox mailbox) throws IOException, CryptoException {
        SecureStorageDir security = mailbox.getStorageDir();
        SecureStorageDir root = SecureStorageDir.createStorage(path, "mailboxes", security.getKeyStore(),
                security.getKeyId());
        baseDir = new SecureStorageDir(root, mailbox.getUid());
        dirtyDir = new SecureStorageDir(baseDir, "dirty");

        this.mailbox = mailbox;

        loadFromDB();
    }

    /**
     * Returns the dirty branches of all remotes.
     * @return
     */
    public List<String> getAllDirtyBranches() {
        List<String> dirtyBranches = new ArrayList<>();
        for (String remote : dirtyRemotes.keySet()) {
            DirtyRemote dirtyRemote = dirtyRemotes.get(remote);
            for (String branch : dirtyRemote.getDirtyBranches()) {
                if (!dirtyBranches.contains(branch))
                    dirtyBranches.add(branch);
            }
        }
        return dirtyBranches;
    }

    private String remoteHash(String remote) {
        return CryptoHelper.toHex(CryptoHelper.sha1Hash(remote.getBytes()));
    }

    public void markAsDirty(String remote, String branch) throws IOException {
        DirtyRemote dirtyRemote;
        if (dirtyRemotes.containsKey(remote))
            dirtyRemote = dirtyRemotes.get(remote);
        else {
            dirtyRemote = new DirtyRemote(remoteHash(remote));
            dirtyRemotes.put(remote, dirtyRemote);
        }
        dirtyRemote.markAsDirty(branch);
    }

    public void cleanDirtyBranch(String remote, String branch) throws IOException {
        if (!dirtyRemotes.containsKey(remote))
            return;
        DirtyRemote dirtyRemote = dirtyRemotes.get(remote);
        dirtyRemote.cleanDirtyBranch(branch);
        if (dirtyRemote.getDirtyBranches().size() == 0)
            dirtyRemotes.remove(remote);
    }

    class DirtyRemote {
        final String remote;
        final private List<String> dirtyBranches;
        final private SecureStorageDir dirtyRemoteDir;

        public DirtyRemote(String remoteHash) {
            this.remote = remoteHash;

            dirtyRemoteDir = new SecureStorageDir(dirtyDir, remoteHash);
            dirtyBranches = readDirtyBranches();
        }

        public void markAsDirty(String branch) throws IOException {
            dirtyRemoteDir.writeString(branch, branch);
            if (!dirtyBranches.contains(branch))
                dirtyBranches.add(branch);
        }

        private void cleanDirtyBranch(String branch) throws IOException {
            dirtyRemoteDir.remove(branch);

            dirtyBranches.remove(branch);
        }

        public List<String> getDirtyBranches() {
            return dirtyBranches;
        }

        private List<String> readDirtyBranches() {
            try {
                return dirtyRemoteDir.listFiles("");
            } catch (IOException e) {
                return new ArrayList<>();
            }
        }
    }

    private void loadFromDB() {
        try {
            List<String> remotes = dirtyDir.listDirectories("");
            for (String remote : remotes)
                dirtyRemotes.put(remote, new DirtyRemote(remote));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class RemoteStatus {
        final private String remote;
        final private SecureStorageDir branchDir;
        // branch -> remote tip
        final private Map<String, String> statusMap = new HashMap<>();

        public RemoteStatus(String remote) {
            this.remote = remote;
            String remoteDirName = remoteHash(remote);
            SecureStorageDir statusDir = new SecureStorageDir(baseDir, "status");
            branchDir = new SecureStorageDir(statusDir, remoteDirName);

            read();
        }

        public void updateRemoteStatus(String branch, String remoteTip) throws IOException {
            String localTip = getLocalTip(branch);
            if (localTip.equals(remoteTip))
                cleanDirtyBranch(remote, branch);

            branchDir.writeString(branch, remoteTip);
        }

        private String getLocalTip(String branch) throws IOException {
            IDatabaseInterface database = DatabaseFactory.getDatabaseFor(
                    mailbox.getStorageDir().getDatabase().getPath(), branch);
            return database.getTip();
        }

        public String getRemoteTip(String branch) {
            return statusMap.get(branch);
        }

        private void read() {
            try {
                List<String> branches = branchDir.listFiles("");
                for (String branch : branches) {
                    String remoteTip = branchDir.readString(branch);
                    statusMap.put(branch, remoteTip);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateRemoteStatus(String remote, String branch, String remoteTip) throws IOException {
        RemoteStatus status = new RemoteStatus(remote);
        status.updateRemoteStatus(branch, remoteTip);
    }

    /**
     * Gets the stored tip of the remote branch.
     *
     * @return the stored tip of the remote branch
     */
    public String getRemoteStatus(String remote, String branch) throws IOException {
        RemoteStatus status = new RemoteStatus(remote);
        return status.getRemoteTip(branch);
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
