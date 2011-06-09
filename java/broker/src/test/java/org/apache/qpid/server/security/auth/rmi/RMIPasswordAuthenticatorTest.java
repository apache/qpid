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
package org.apache.qpid.server.security.auth.rmi;

import java.util.Collections;

import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import junit.framework.TestCase;

import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;

/**
 * Tests the RMIPasswordAuthenticator and its collaboration with the AuthenticationManager.
 *
 */
public class RMIPasswordAuthenticatorTest extends TestCase
{
    private final String USERNAME = "guest";
    private final String PASSWORD = "guest";
    private RMIPasswordAuthenticator _rmipa;
    private String[] _credentials;

    protected void setUp() throws Exception
    {
        _rmipa = new RMIPasswordAuthenticator();
        
        _credentials = new String[] {USERNAME, PASSWORD};
    }

    /**
     * Tests a successful authentication.  Ensures that a populated read-only subject it returned.
     */
    public void testAuthenticationSuccess()
    {
        final Subject expectedSubject = new Subject(true,
                Collections.singleton(new JMXPrincipal(USERNAME)),
                Collections.EMPTY_SET,
                Collections.EMPTY_SET);

        _rmipa.setAuthenticationManager(createTestAuthenticationManager(true, null));


        Subject newSubject = _rmipa.authenticate(_credentials);
        assertTrue("Subject must be readonly", newSubject.isReadOnly());
        assertTrue("Returned subject does not equal expected value",
                newSubject.equals(expectedSubject));

    }
    
    /**
     * Tests a unsuccessful authentication.
     */
    public void testUsernameOrPasswordInvalid()
    {
        _rmipa.setAuthenticationManager(createTestAuthenticationManager(false, null));
        
        try
        {
            _rmipa.authenticate(_credentials);
            fail("Exception not thrown");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    RMIPasswordAuthenticator.INVALID_CREDENTIALS, se.getMessage());

        }
    }

    /**
     * Tests case where authentication system itself fails.
     */
    public void testAuthenticationFailure()
    {
        final Exception mockAuthException = new Exception("Mock Auth system failure");
        _rmipa.setAuthenticationManager(createTestAuthenticationManager(false, mockAuthException));

        try
        {
            _rmipa.authenticate(_credentials);
            fail("Exception not thrown");
        }
        catch (SecurityException se)
        {
            assertEquals("Initial cause not found", mockAuthException, se.getCause());
        }
    }


    /**
     * Tests case where authentication manager is not set.
     */
    public void testNullAuthenticationManager()
    {
        try
        {
            _rmipa.authenticate(_credentials);
            fail("SecurityException expected due to lack of authentication manager");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    RMIPasswordAuthenticator.UNABLE_TO_LOOKUP, se.getMessage());
        }
    }

    /**
     * Tests case where arguments are non-Strings..
     */
    public void testWithNonStringArrayArgument()
    {
        // Test handling of non-string credential's
        final Object[] objCredentials = new Object[]{USERNAME, PASSWORD};
        try
        {
             _rmipa.authenticate(objCredentials);
            fail("SecurityException expected due to non string[] credentials");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    RMIPasswordAuthenticator.SHOULD_BE_STRING_ARRAY, se.getMessage());
        }
    }

    /**
     * Tests case where there are too many, too few or null arguments.
     */
    public void testWithIllegalNumberOfArguments()
    {
        // Test handling of incorrect number of credentials
        try
        {
            _credentials = new String[]{USERNAME, PASSWORD, PASSWORD};
            _rmipa.authenticate(_credentials);
            fail("SecurityException expected due to supplying wrong number of credentials");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    RMIPasswordAuthenticator.SHOULD_HAVE_2_ELEMENTS, se.getMessage());
        }
        
        // Test handling of null credentials
        try
        {
            //send a null array
            _credentials = null;
            _rmipa.authenticate(_credentials);
            fail("SecurityException expected due to not supplying an array of credentials");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    RMIPasswordAuthenticator.CREDENTIALS_REQUIRED, se.getMessage());
        }
        
        try
        {
            //send a null password
            _credentials = new String[]{USERNAME, null};
            _rmipa.authenticate(_credentials);
            fail("SecurityException expected due to sending a null password");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    RMIPasswordAuthenticator.SHOULD_BE_NON_NULL, se.getMessage());
        }
        
        try
        {
            //send a null username
            _credentials = new String[]{null, PASSWORD};
            _rmipa.authenticate(_credentials);
            fail("SecurityException expected due to sending a null username");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    RMIPasswordAuthenticator.SHOULD_BE_NON_NULL, se.getMessage());
        }
    }

    private AuthenticationManager createTestAuthenticationManager(final boolean successfulAuth, final Exception exception)
    {
        return new AuthenticationManager()
        {
            @Override
            public void close()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getMechanisms()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public SaslServer createSaslServer(String mechanism, String localFQDN) throws SaslException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public AuthenticationResult authenticate(SaslServer server, byte[] response)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public AuthenticationResult authenticate(String username, String password)
            {
                if (exception != null) {
                    return new AuthenticationResult(AuthenticationStatus.ERROR, exception);
                }
                else if (successfulAuth)
                {
                    return new AuthenticationResult(new Subject());
                }
                else
                {
                    return new AuthenticationResult(AuthenticationStatus.CONTINUE);
                }
            }
        };
    }

}
