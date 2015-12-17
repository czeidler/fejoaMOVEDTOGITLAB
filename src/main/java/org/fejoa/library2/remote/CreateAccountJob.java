/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2.remote;

import org.eclipse.jgit.util.Base64;
import org.fejoa.library.crypto.*;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;


public class CreateAccountJob extends SimpleJsonRemoteJob<RemoteJob.Result> {
    static final public String METHOD = "createAccount";

    static final public String USER_NAME_KEY = "userName";
    static final public String PASSWORD_KEY = "password";
    static final public String USER_DATA_BRANCH_KEY = "userDataBranch";
    static final public String SALT_BASE64_KEY = "saltBase64";
    static final public String KDF_ALGORITHM_KEY = "kdfAlgorithm";
    static final public String KEY_SIZE_KEY = "keySize";
    static final public String KDF_ITERATIONS_KEY = "kdfIterations";

    final private String userName;
    final private String password;
    final private String userDataBranch;
    final private CryptoSettings.Password settings;

    public CreateAccountJob(String userName, String password, String userDataBranch, CryptoSettings.Password settings) {
        super(false);

        this.userName = userName;
        this.password = password;
        this.userDataBranch = userDataBranch;
        this.settings = settings;
    }

    static public String makeServerPassword(String password, byte[] salt, String kdfAlgorithm, int keySize,
                                            int kdfIterations) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        SecretKey secretKey = crypto.deriveKey(password, salt, kdfAlgorithm, keySize, kdfIterations);
        return CryptoHelper.sha256HashHex(secretKey.getEncoded());
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        ICryptoInterface crypto = Crypto.get();
        byte[] salt = crypto.generateSalt();
        String saltBase64 = Base64.encodeBytes(salt);
        String derivedPassword;

        try {
            derivedPassword = makeServerPassword(password, salt, settings.kdfAlgorithm, settings.keySize,
                    settings.kdfIterations);
        } catch (CryptoException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }

        return jsonRPC.call(METHOD, new JsonRPC.Argument(USER_NAME_KEY, userName),
                new JsonRPC.Argument(PASSWORD_KEY, derivedPassword),
                new JsonRPC.Argument(USER_DATA_BRANCH_KEY, userDataBranch),
                new JsonRPC.Argument(SALT_BASE64_KEY, saltBase64),
                new JsonRPC.Argument(KDF_ALGORITHM_KEY, settings.kdfAlgorithm),
                new JsonRPC.Argument(KEY_SIZE_KEY, settings.keySize),
                new JsonRPC.Argument(KDF_ITERATIONS_KEY, settings.kdfIterations));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        return getResult(returnValue);
    }
}
