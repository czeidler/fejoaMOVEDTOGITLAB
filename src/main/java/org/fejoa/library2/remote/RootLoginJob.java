/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.bouncycastle.util.encoders.Base64;
import org.fejoa.library.crypto.CryptoException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;


public class RootLoginJob extends SimpleJsonRemoteJob {
    static final public String METHOD = "rootLogin";
    static final public String REQUEST_KEY = "request";
    static final public String PARAMETER_REQUEST = "getParameters";
    static final public String LOGIN_REQUEST = "login";

    static public class SendPasswordJob extends SimpleJsonRemoteJob {
        final private String userName;
        final private String serverPassword;

        public SendPasswordJob(String userName, String serverPassword) {
            super(false);

            this.userName = userName;
            this.serverPassword = serverPassword;
        }

        @Override
        public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
            return jsonRPC.call(METHOD, new JsonRPC.Argument(REQUEST_KEY, LOGIN_REQUEST),
                    new JsonRPC.Argument(CreateAccountJob.USER_NAME_KEY, userName),
                    new JsonRPC.Argument(CreateAccountJob.PASSWORD_KEY, serverPassword));
        }

        @Override
        protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
            return getResult(returnValue);
        }
    }

    final private String userName;
    final private String password;

    public RootLoginJob(String userName, String password) {
        super(false);

        this.userName = userName;
        this.password = password;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        return jsonRPC.call(METHOD, new JsonRPC.Argument(REQUEST_KEY, PARAMETER_REQUEST),
                new JsonRPC.Argument(CreateAccountJob.USER_NAME_KEY, userName));
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
