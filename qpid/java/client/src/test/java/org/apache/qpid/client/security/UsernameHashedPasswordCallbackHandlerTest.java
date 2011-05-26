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
package org.apache.qpid.client.security;

import java.security.MessageDigest;
import java.util.Arrays;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.MockAMQConnection;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.protocol.AMQProtocolSession;

/**
 * Unit tests for the UsernameHashPasswordCallbackHandler.  This callback handler is
 * used by the CRAM-MD5-HASHED SASL mechanism.
 *
 */
public class UsernameHashedPasswordCallbackHandlerTest extends TestCase
{
    private AMQCallbackHandler _callbackHandler = new UsernameHashedPasswordCallbackHandler(); // Class under test
    private static final String PROMPT_UNUSED = "unused";

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        final String url = "amqp://username:password@client/test?brokerlist='vm://:1'";
        _callbackHandler.initialise(new AMQConnectionURL(url));
    }

    /**
     *  Tests that the callback handler can correctly retrieve the username from the connection url.
     */
    public void testNameCallback() throws Exception
    {
        final String expectedName = "username";
        NameCallback nameCallback = new NameCallback(PROMPT_UNUSED);

        assertNull("Unexpected name before test", nameCallback.getName());
        _callbackHandler.handle(new Callback[] {nameCallback});
        assertEquals("Unexpected name", expectedName, nameCallback.getName());
    }

    /**
     *  Tests that the callback handler can correctly retrieve the password from the connection url
     *  and calculate a MD5.
     */
    public void testDigestedPasswordCallback() throws Exception
    {
        final char[] expectedPasswordDigested = getHashPassword("password");
        
        PasswordCallback passwordCallback = new PasswordCallback(PROMPT_UNUSED, false);
        assertNull("Unexpected password before test", passwordCallback.getPassword());
        _callbackHandler.handle(new Callback[] {passwordCallback});
        assertTrue("Unexpected password", Arrays.equals(expectedPasswordDigested, passwordCallback.getPassword()));
    }

    private char[] getHashPassword(final String password) throws Exception
    {
        MessageDigest md5Digester = MessageDigest.getInstance("MD5");
        final byte[] digest = md5Digester.digest(password.getBytes("UTF-8"));

        char[] hash = new char[digest.length];

        int index = 0;
        for (byte b : digest)
        {
            hash[index++] = (char) b;
        }

        return hash;
    }
}
