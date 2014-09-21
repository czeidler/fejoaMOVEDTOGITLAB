/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;


public class Crypto {
    static private ICryptoInterface cryptoInterface = null;

    static public ICryptoInterface get() {
        if (cryptoInterface == null)
            cryptoInterface = new BCCryptoInterface();
        return cryptoInterface;
    }
}
