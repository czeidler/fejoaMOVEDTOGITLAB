/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.SecureStorageDir;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.SecureStorageDirBucket;
import org.fejoa.library.remote.ConnectionInfo;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
    // remoteId name -> DirtyRemote
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
        for (String remoteId : dirtyRemotes.keySet()) {
            DirtyRemote dirtyRemote = dirtyRemotes.get(remoteId);
            for (String branch : dirtyRemote.getDirtyBranches()) {
                if (!dirtyBranches.contains(branch))
                    dirtyBranches.add(branch);
            }
        }
        return dirtyBranches;
    }

    public void markAsDirty(String remote, String branch) throws IOException {
        DirtyRemote dirtyRemote;
        String remoteId = CryptoHelper.sha1HashHex(remote);
        if (dirtyRemotes.containsKey(remoteId))
            dirtyRemote = dirtyRemotes.get(remoteId);
        else {
            dirtyRemote = new DirtyRemote(remote);
            dirtyRemotes.put(remoteId, dirtyRemote);
        }
        dirtyRemote.markAsDirty(branch);
    }

    public void cleanDirtyBranch(String remoteId, String branch) throws IOException {
        if (!dirtyRemotes.containsKey(remoteId))
            return;
        DirtyRemote dirtyRemote = dirtyRemotes.get(remoteId);
        dirtyRemote.cleanDirtyBranch(branch);
        if (dirtyRemote.getDirtyBranches().size() == 0)
            dirtyRemotes.remove(remoteId);
    }

    class DirtyRemote {
        final String remoteId;
        final private List<String> dirtyBranches;
        final private SecureStorageDir dirtyRemoteDir;

        public DirtyRemote(String remote) {
            this.remoteId = ConnectionInfo.getRemoteId(remote);

            dirtyRemoteDir = new SecureStorageDir(dirtyDir, remoteId);
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
        final private String remoteId;
        final private SecureStorageDir branchDir;
        // branch -> remoteId tip
        final private Map<String, String> statusMap = new HashMap<>();

        public RemoteStatus(String remoteId) {
            this.remoteId = remoteId;
            SecureStorageDir statusDir = new SecureStorageDir(baseDir, "status");
            branchDir = new SecureStorageDir(statusDir, remoteId);

            read();
        }

        public void updateRemoteStatus(String branch, String remoteTip) throws IOException {
            String localTip = getLocalTip(branch);
            if (localTip.equals(remoteTip))
                cleanDirtyBranch(remoteId, branch);

            branchDir.writeString(branch, remoteTip);
        }

        private String getLocalTip(String branch) throws IOException {
            return SecureStorageDirBucket.get(mailbox.getStorageDir().getPath(), branch).getTip();
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
     * Gets the stored tip of the remoteId branch.
     *
     * @return the stored tip of the remoteId branch
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

    public void commit() throws IOException {
        baseDir.commit();
    }

    public String getMailboxTip() {
        try {
            return baseDir.readString("mailboxTip");
        } catch (IOException e) {
            return "";
        }
    }
}
