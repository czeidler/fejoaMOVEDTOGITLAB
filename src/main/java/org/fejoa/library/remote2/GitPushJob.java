/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import java.io.*;


public class GitPushJob extends JsonRemoteJob {
    static final public String METHOD = "gitPush";

    @Override
    public Result run(IRemoteRequest remoteRequest) throws IOException {
        super.run(remoteRequest);

        String header = jsonRPC.call(METHOD);
        RemotePipe pipe = new RemotePipe(header, remoteRequest, null);
        OutputStream outputStream = pipe.getOutputStream();
        InputStream inputStream = pipe.getInputStream();

        outputStream.write("test".getBytes());
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String response = reader.readLine();

        outputStream.write(response.getBytes());
        response = reader.readLine();

        return new Result(Result.DONE, "ok" + response);
    }
}
