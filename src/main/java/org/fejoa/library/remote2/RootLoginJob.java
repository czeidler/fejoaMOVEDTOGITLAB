/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote2;

import org.bouncycastle.util.encoders.Base64;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;


public class RootLoginJob extends SimpleJsonRemoteJob {
    static public class SendPasswordJob extends SimpleJsonRemoteJob {
        static final public String METHOD = "rootLogin";

        final private String userName;
        final private String serverPassword;

        public SendPasswordJob(String userName, String serverPassword) {
            super(false);

            this.userName = userName;
            this.serverPassword = serverPassword;
        }

        @Override
        public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
            return jsonRPC.call(METHOD, new JsonRPC.Argument(CreateAccountJob.USER_NAME_KEY, userName),
                    new JsonRPC.Argument(CreateAccountJob.PASSWORD_KEY, serverPassword));
        }

        @Override
        protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
            return getResult(returnValue);
        }
    }

    static final public String METHOD = "getRootLoginParameters";

    final private String userName;
    final private String password;

    public RootLoginJob(String userName, String password) {
        super(false);

        this.userName = userName;
        this.password = password;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        return jsonRPC.call(METHOD, new JsonRPC.Argument(CreateAccountJob.USER_NAME_KEY, userName));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        try {
            byte[] salt = Base64.decode(returnValue.getString(CreateAccountJob.SALT_BASE64_KEY));
            String algorithm = returnValue.getString(CreateAccountJob.KDF_ALGORITHM_KEY);
            int keySize = returnValue.getInt(CreateAccountJob.KEY_SIZE_KEY);
            int iterations = returnValue.getInt(CreateAccountJob.KDF_ITERATIONS_KEY);

            String serverPassword = CreateAccountJob.makeServerPassword(password, salt, algorithm, keySize, iterations);
            setFollowUpJob(new SendPasswordJob(userName, serverPassword));
            return new Result(Result.FOLLOW_UP_JOB, "parameters received");
        } catch (JSONException e) {
            e.printStackTrace();
            return new Result(Result.ERROR, "parameter missing");
        } catch (CryptoException e) {
            e.printStackTrace();
            return new Result(Result.ERROR, "parameter missing");
        }
    }
}
