/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.mailbox;

import org.fejoa.library.Contact;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.IContactFinder;
import org.fejoa.library.KeyId;
import org.fejoa.library.crypto.*;
import org.fejoa.library.support.PositionInputStream;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;


interface IParcelEnvelopeWriter {
    public byte[] pack(byte[] parcel) throws IOException, CryptoException;
}

interface IParcelEnvelopeReader {
    public byte[] unpack(byte[] parcel) throws IOException, CryptoException;
}


