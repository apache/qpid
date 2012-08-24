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

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.security.Principal;

import javax.security.auth.Subject;

import junit.framework.TestCase;

import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;

/**
 * Tests the RMIPasswordAuthenticator and its collaboration with the AuthenticationManager.
 *
 */
public class RMIPasswordAuthenticatorTest extends TestCase
{
    private static final Subject SUBJECT = new Subject();
    private final String USERNAME = "guest";
    private final String PASSWORD = "guest";
    private RMIPasswordAuthenticator _rmipa;
    private String[] _credentials;

    protected void setUp() throws Exception
    {
        _rmipa = new RMIPasswordAuthenticator(new InetSocketAddress(5672));

        _credentials = new String[] {USERNAME, PASSWORD};
    }

    /**
     * Tests a successful authentication.  Ensures that the expected subject is returned.
     */
    public void testAuthenticationSuccess()
    {
        _rmipa.setSubjectCreator(createMockSubjectCreator(true, null));

        Subject newSubject = _rmipa.authenticate(_credentials);
        assertSame("Subject must be unchanged", SUBJECT, newSubject);
    }

    /**
     * Tests a unsuccessful authentication.
     */
    public void testUsernameOrPasswordInvalid()
    {
        _rmipa.setSubjectCreator(createMockSubjectCreator(false, null));

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
        _rmipa.setSubjectCreator(createMockSubjectCreator(false, mockAuthException));

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
    public void testNullAuthenticationManager() throws Exception
    {
        _rmipa.setSubjectCreator(null);
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

    private SubjectCreator createMockSubjectCreator(final boolean successfulAuth, final Exception exception)
    {
        SubjectCreator subjectCreator = mock(SubjectCreator.class);

        SubjectAuthenticationResult subjectAuthenticationResult;

        if (exception != null) {

            subjectAuthenticationResult = new SubjectAuthenticationResult(
                    new AuthenticationResult(AuthenticationStatus.ERROR, exception));
        }
        else if (successfulAuth)
        {

            subjectAuthenticationResult = new SubjectAuthenticationResult(
                    new AuthenticationResult(mock(Principal.class)), SUBJECT);
        }
        else
        {
            subjectAuthenticationResult = new SubjectAuthenticationResult(new AuthenticationResult(AuthenticationStatus.CONTINUE));
        }

        when(subjectCreator.authenticate(anyString(), anyString())).thenReturn(subjectAuthenticationResult);

        return subjectCreator;
    }
}
