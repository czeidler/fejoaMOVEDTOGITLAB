/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefAdvertiser;
import org.eclipse.jgit.transport.UploadPack;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.remote2.GitPullJob;
import org.fejoa.library.remote2.JsonRPCHandler;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;


public class GitPullHandler extends JsonRequestHandler {
    public GitPullHandler() {
        super(GitPullJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String request = params.getString("request");
        if (request.equals(GitPullJob.METHOD_REQUEST_ADVERTISEMENT)) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.OK, "send advertisement"));

            JGitInterface gitInterface = new JGitInterface();
            gitInterface.init(".gitTest", "testBranch", true);
            Repository repository = gitInterface.getRepository();

            ReceivePack receivePack = new ReceivePack(repository);
            OutputStream rawOut = responseHandler.addData();
            PacketLineOut pckOut = new PacketLineOut(rawOut);
            pckOut.setFlushOnEnd(false);
            RefAdvertiser refAdvertiser = new RefAdvertiser.PacketLineOutRefAdvertiser(pckOut);
            refAdvertiser.advertiseCapability("multi_ack_detailed");
            receivePack.sendAdvertisedRefs(refAdvertiser);
        } else if (request.equals(GitPullJob.METHOD_REQUEST_PULL_DATA)) {
            ServerPipe pipe = new ServerPipe(jsonRPCHandler.makeResult(Portal.Errors.OK, "receive push data"),
                    responseHandler, data);

            JGitInterface gitInterface = new JGitInterface();
            gitInterface.init(".gitTest", "testBranch", true);
            Repository repository = gitInterface.getRepository();

            UploadPack uploadPack = new UploadPack(repository);
            uploadPack.setBiDirectionalPipe(false);
            uploadPack.upload(pipe.getInputStream(), pipe.getOutputStream(), null);

        } else {
            throw new Exception("Invalid pull request: " + request);
        }
    }
}
