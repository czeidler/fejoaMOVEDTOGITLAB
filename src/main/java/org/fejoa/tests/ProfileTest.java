/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests;

import junit.framework.TestCase;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.Profile;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.FejoaEnvironment;
import org.fejoa.library.UserIdentity;
import org.fejoa.library.mailbox.*;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class ProfileTest extends TestCase {
    final List<String> cleanUpDirs = new ArrayList<String>();

    @Override
    public void setUp() throws Exception {
        super.setUp();

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testProfile() throws Exception {
        FejoaEnvironment environment = new FejoaEnvironment("");
        final String password = "password";
        final String userName = "testName";
        final String server = "localhost";
        final String messageBody = "message body";
        final String subject = "test branch";

        // creation
        Profile profile = new Profile(environment, "profile", "");
        cleanUpDirs.add(profile.getStorageDir().getDatabasePath());
        CryptoSettings settings = CryptoSettings.getFast();

        profile.createNew(password, settings);

        ContactPrivate myself = profile.getMainUserIdentity().getMyself();
        myself.setServerUser(userName);
        myself.setServer(server);
        myself.write();

        profile.setEmptyRemotes(server, userName, myself);

        UserIdentity userIdentity = profile.getMainUserIdentity();
        final String userIdentityIDCreation = userIdentity.getUid();
        final String myselfIDCreation = userIdentity.getMyself().getUid();
        profile.commit();

        // open
        environment = new FejoaEnvironment("");
        profile = new Profile(environment, "profile", "");
        assertTrue(profile.open(password));

        userIdentity = profile.getMainUserIdentity();
        myself = userIdentity.getMyself();
        assertEquals(userIdentityIDCreation, userIdentity.getUid());
        assertEquals(myselfIDCreation, myself.getUid());
        assertEquals(userName, myself.getServerUser());
        assertEquals(server, myself.getServer());

        // mailbox
        Mailbox mailbox = profile.getMainMailbox();
        MessageChannel messageChannel = mailbox.createNewMessageChannel();
        // setup branch
        MessageBranchInfo branchInfo = new MessageBranchInfo();
        branchInfo.addParticipant(myself.getAddress(), myself.getUid());
        branchInfo.addParticipant("peter@non.com", "fakeUI1");
        branchInfo.addParticipant("otto@non.de", "fakeUI2");
        branchInfo.setSubject(subject);
        MessageBranch messageBranch = messageChannel.getBranch();
        messageBranch.setMessageBranchInfo(branchInfo, settings.signature);
        // add messages
        Message message = new Message();
        message.setBody(messageBody);
        messageBranch.addMessage(message, settings.signature);
        // and add it to the mailbox
        mailbox.addMessageChannel(messageChannel);
        messageBranch.commit();
        mailbox.commit();
        mailbox.clearMessageChannelCache();

        // read message and branch
        assertEquals(1, mailbox.getNumberOfMessageChannels());
        Mailbox.MessageChannelRef messageChannelRef = mailbox.getMessageChannel(0);
        messageChannel = messageChannelRef.getSync();
        messageBranch = messageChannel.getBranch();
        assertEquals(1, messageBranch.getNumberOfMessages());
        message = messageBranch.getMessage(0);
        assertEquals(messageBody, message.getBody());
        branchInfo = messageBranch.getMessageBranchInfo();
        assertTrue(branchInfo != null);
        assertEquals(subject, branchInfo.getSubject());
        List<MessageBranchInfo.Participant> participants = branchInfo.getParticipants();
        assertEquals(3, participants.size());
    }
}
