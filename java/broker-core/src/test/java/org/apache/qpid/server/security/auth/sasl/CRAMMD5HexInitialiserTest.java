/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.security.auth.sasl;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.xml.bind.DatatypeConverter;

import junit.framework.TestCase;

import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HexInitialiser;
import org.apache.qpid.test.utils.TestFileUtils;

/**
 * These tests ensure that the Hex wrapping that the initialiser performs does actually operate when the handle method is called.
 */
public class CRAMMD5HexInitialiserTest extends TestCase
{
    private static final String TEST_PASSWORD = "testPassword";
    private static final String TEST_USER = "testUser";
    private File _file;

    public void testHashedHex() throws Exception
    {
        perform(TEST_USER, getHash(TEST_PASSWORD));
    }

    public void perform(String user, char[] password) throws Exception
    {
        CRAMMD5HexInitialiser initialiser = new CRAMMD5HexInitialiser();

        PrincipalDatabase db = new Base64MD5PasswordFilePrincipalDatabase();
        db.open(_file);
        initialiser.initialise(db);

        PasswordCallback passwordCallback = new PasswordCallback("password:", false);
        NameCallback usernameCallback = new NameCallback("user:", user);

        Callback[] callbacks = new Callback[]{usernameCallback, passwordCallback};

        assertNull("The password was not null before the handle call.", passwordCallback.getPassword());
        initialiser.getCallbackHandler().handle(callbacks);

        assertArrayEquals(toHex(password), passwordCallback.getPassword());
    }

    public void setUp() throws Exception
    {
        super.setUp();

        MessageDigest md = MessageDigest.getInstance("MD5");

        md.update(TEST_PASSWORD.getBytes("utf-8"));

        _file = TestFileUtils.createTempFile(this, "password-file",
                                             TEST_USER + ":" + DatatypeConverter.printBase64Binary(md.digest()));
    }

    public void tearDown() throws Exception
    {
        if (_file != null)
        {
            _file.delete();
        }
        super.tearDown();
    }

    private char[] getHash(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException
    {

        byte[] data = text.getBytes("utf-8");

        MessageDigest md = MessageDigest.getInstance("MD5");

        for (byte b : data)
        {
            md.update(b);
        }

        byte[] digest = md.digest();

        char[] hash = new char[digest.length];

        int index = 0;
        for (byte b : digest)
        {
            hash[index++] = (char) b;
        }

        return hash;
    }

    private void assertArrayEquals(char[] expected, char[] actual)
    {        
        assertEquals("Arrays are not the same length", expected.length, actual.length);

        for (int index = 0; index < expected.length; index++)
        {
            assertEquals("Characters are not equal", expected[index], actual[index]);
        }
    }

    private char[] toHex(char[] password)
    {
        StringBuilder sb = new StringBuilder();
        for (char c : password)
        {
            //toHexString does not prepend 0 so we have to
            if (((byte) c > -1) && (byte) c < 10)
            {
                sb.append(0);
            }

            sb.append(Integer.toHexString(c & 0xFF));
        }

        //Extract the hex string as char[]
        char[] hex = new char[sb.length()];

        sb.getChars(0, sb.length(), hex, 0);

        return hex;
    }

}
