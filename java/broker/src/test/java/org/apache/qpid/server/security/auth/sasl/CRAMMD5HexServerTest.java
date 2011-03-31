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
import java.io.IOException;
import java.security.MessageDigest;
import java.security.Principal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import junit.framework.TestCase;

import org.apache.commons.codec.binary.Hex;
import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HexInitialiser;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HexSaslServer;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HexServerFactory;

/**
 * Test for the CRAM-MD5-HEX SASL mechanism.
 *
 * This test case focuses on testing {@link CRAMMD5HexSaslServer} but also exercises
 * collaborators {@link CRAMMD5HexInitialiser} and {@link Base64MD5PasswordFilePrincipalDatabase}
 */
public class CRAMMD5HexServerTest extends TestCase
{

    private SaslServer _saslServer;  // Class under test
    private CRAMMD5HexServerFactory _saslFactory;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        CRAMMD5HexInitialiser _initializer = new CRAMMD5HexInitialiser();

        //Use properties to create a PrincipalDatabase
        Base64MD5PasswordFilePrincipalDatabase db = createTestPrincipalDatabase();
        assertEquals("Unexpected number of test users in the db", 2, db.getUsers().size());

        _initializer.initialise(db);

        _saslFactory = new CRAMMD5HexServerFactory();

        _saslServer = _saslFactory.createSaslServer(CRAMMD5HexSaslServer.MECHANISM,
                "AMQP",
                "localhost",
                _initializer.getProperties(),
                _initializer.getCallbackHandler());
        assertNotNull("Unable to create saslServer with mechanism type " + CRAMMD5HexSaslServer.MECHANISM, _saslServer);

    }

    public void testSuccessfulAuth() throws Exception
    {

        final byte[] serverChallenge = _saslServer.evaluateResponse(new byte[0]);

        // Generate client response
        final byte[] clientResponse = generateClientResponse("knownuser", "guest", serverChallenge);


        byte[] nextServerChallenge = _saslServer.evaluateResponse(clientResponse);
        assertTrue("Exchange must be flagged as complete after successful authentication", _saslServer.isComplete());
        assertNull("Next server challenge must be null after successful authentication", nextServerChallenge);

    }

    public void testKnownUserPresentsWrongPassword() throws Exception
    {
        byte[] serverChallenge = _saslServer.evaluateResponse(new byte[0]);


        final byte[] clientResponse = generateClientResponse("knownuser", "wrong!", serverChallenge);
        try
        {
            _saslServer.evaluateResponse(clientResponse);
            fail("Exception not thrown");
        }
        catch (SaslException se)
        {
            // PASS
        }
        assertFalse("Exchange must not be flagged as complete after unsuccessful authentication", _saslServer.isComplete());
    }

    public void testUnknownUser() throws Exception
    {
        final byte[] serverChallenge = _saslServer.evaluateResponse(new byte[0]);


        final byte[] clientResponse = generateClientResponse("unknownuser", "guest", serverChallenge);

        try
        {
            _saslServer.evaluateResponse(clientResponse);
            fail("Exception not thrown");
        }
        catch (SaslException se)
        {
            assertExceptionHasUnderlyingAsCause(AccountNotFoundException.class, se);
            // PASS
        }
        assertFalse("Exchange must not be flagged as complete after unsuccessful authentication", _saslServer.isComplete());
    }

    /**
     *
     * Demonstrates QPID-3158.  A defect meant that users with some valid password were failing to 
     * authenticate when using the .NET 0-8 client (uses this SASL mechanism).  
     * It so happens that password "guest2" was one of the affected passwords.
     *
     * @throws Exception
     */
    public void testSuccessfulAuthReproducingQpid3158() throws Exception
    {
        byte[] serverChallenge = _saslServer.evaluateResponse(new byte[0]);

        // Generate client response
        byte[] resp = generateClientResponse("qpid3158user", "guest2", serverChallenge);

        byte[] nextServerChallenge = _saslServer.evaluateResponse(resp);
        assertTrue("Exchange must be flagged as complete after successful authentication", _saslServer.isComplete());
        assertNull("Next server challenge must be null after successful authentication", nextServerChallenge);
    }

    /**
     * Since we don't have a CRAM-MD5-HEX implementation client implementation in Java, this method
     * provides the implementation for first principals.
     *
     * @param userId user id
     * @param clearTextPassword clear text password
     * @param serverChallenge challenge from server
     *
     * @return challenge response
     */
    private byte[] generateClientResponse(final String userId, final String clearTextPassword, final byte[] serverChallenge) throws Exception
    {
        byte[] digestedPasswordBytes = MessageDigest.getInstance("MD5").digest(clearTextPassword.getBytes());
        char[] hexEncodedDigestedPassword = Hex.encodeHex(digestedPasswordBytes);
        byte[] hexEncodedDigestedPasswordBytes = new String(hexEncodedDigestedPassword).getBytes();


        Mac hmacMd5 = Mac.getInstance("HmacMD5");
        hmacMd5.init(new SecretKeySpec(hexEncodedDigestedPasswordBytes, "HmacMD5"));
        final byte[] messageAuthenticationCode = hmacMd5.doFinal(serverChallenge);

        // Build client response
        String responseAsString = userId + " " + new String(Hex.encodeHex(messageAuthenticationCode));
        byte[] resp = responseAsString.getBytes();
        return resp;
    }

    /**
     * Creates a test principal database.
     *
     * @return
     * @throws IOException
     */
    private Base64MD5PasswordFilePrincipalDatabase createTestPrincipalDatabase() throws IOException
    {
        Base64MD5PasswordFilePrincipalDatabase db = new Base64MD5PasswordFilePrincipalDatabase();
        File file = File.createTempFile("passwd", "db");
        file.deleteOnExit();
        db.setPasswordFile(file.getCanonicalPath());
        db.createPrincipal( createTestPrincipal("knownuser"), "guest".toCharArray());
        db.createPrincipal( createTestPrincipal("qpid3158user"), "guest2".toCharArray());
        return db;
    }

    private Principal createTestPrincipal(final String name)
    {
        return new Principal()
        {

            @Override
            public String getName()
            {
                return name;
            }
        };
    }
    
    private void assertExceptionHasUnderlyingAsCause(final Class<? extends Throwable> expectedUnderlying, Throwable e)
    {
        assertNotNull(e);
        int infiniteLoopGuard = 0;  // Guard against loops in the cause chain
        boolean foundExpectedUnderlying = false;
        while (e.getCause() != null && infiniteLoopGuard++ < 10)
        {
            if (expectedUnderlying.equals(e.getCause().getClass()))
            {
                foundExpectedUnderlying = true;
                break;
            }
            e = e.getCause();
        }
        
        if (!foundExpectedUnderlying)
        {
            fail("Not found expected underlying exception " + expectedUnderlying + " as underlying cause of " + e.getClass());
        }
    }

}
