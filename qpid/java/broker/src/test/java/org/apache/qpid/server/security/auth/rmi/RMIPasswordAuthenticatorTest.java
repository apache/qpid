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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Principal;
import java.util.regex.Pattern;

import javax.security.auth.Subject;

import junit.framework.TestCase;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.security.SecurityManager;

/**
 * Tests the RMIPasswordAuthenticator and its collaboration with the AuthenticationManager.
 *
 */
public class RMIPasswordAuthenticatorTest extends TestCase
{
    private static final String USERNAME = "guest";
    private static final String PASSWORD = "password";

    private final Broker _broker = mock(Broker.class);
    private final SecurityManager _securityManager = mock(SecurityManager.class);
    private final Subject _loginSubject = new Subject();
    private final String[] _credentials = new String[] {USERNAME, PASSWORD};

    private RMIPasswordAuthenticator _rmipa;

    private SubjectCreator _usernamePasswordOkaySuvjectCreator = createMockSubjectCreator(true, null);
    private SubjectCreator _badPasswordSubjectCreator = createMockSubjectCreator(false, null);

    protected void setUp() throws Exception
    {
        when(_broker.getSecurityManager()).thenReturn(_securityManager);
        _rmipa = new RMIPasswordAuthenticator(_broker, new InetSocketAddress(8999));
    }

    /**
     * Tests a successful authentication.  Ensures that the expected subject is returned.
     */
    public void testAuthenticationSuccess()
    {
        when(_broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(_usernamePasswordOkaySuvjectCreator);
        when(_securityManager.accessManagement()).thenReturn(true);

        Subject newSubject = _rmipa.authenticate(_credentials);
        assertSame("Subject must be unchanged", _loginSubject, newSubject);
    }

    /**
     * Tests a unsuccessful authentication.
     */
    public void testUsernameOrPasswordInvalid()
    {
        when(_broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(_badPasswordSubjectCreator);

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

    public void testAuthorisationFailure()
    {
        when(_broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(_usernamePasswordOkaySuvjectCreator);
        when(_securityManager.accessManagement()).thenReturn(false);

        try
        {
            _rmipa.authenticate(_credentials);
            fail("Exception not thrown");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    RMIPasswordAuthenticator.USER_NOT_AUTHORISED_FOR_MANAGEMENT, se.getMessage());
        }
    }

    public void testSubjectCreatorInternalFailure()
    {
        final Exception mockAuthException = new Exception("Mock Auth system failure");
        SubjectCreator subjectCreator = createMockSubjectCreator(false, mockAuthException);
        when(_broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(subjectCreator);

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
    public void testNullSubjectCreator() throws Exception
    {
        when(_broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(null);

        try
        {
            _rmipa.authenticate(_credentials);
            fail("SecurityException expected due to lack of authentication manager");
        }
        catch (SecurityException se)
        {
            assertTrue("Unexpected exception message", Pattern.matches("Can't get subject creator for .*:8999", se.getMessage()));
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
        String[] credentials;

        // Test handling of incorrect number of credentials
        try
        {
            credentials = new String[]{USERNAME, PASSWORD, PASSWORD};
            _rmipa.authenticate(credentials);
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
            credentials = null;
            _rmipa.authenticate(credentials);
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
            credentials = new String[]{USERNAME, null};
            _rmipa.authenticate(credentials);
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
            credentials = new String[]{null, PASSWORD};
            _rmipa.authenticate(credentials);
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
                    new AuthenticationResult(mock(Principal.class)), _loginSubject);
        }
        else
        {
            subjectAuthenticationResult = new SubjectAuthenticationResult(new AuthenticationResult(AuthenticationStatus.CONTINUE));
        }

        when(subjectCreator.authenticate(anyString(), anyString())).thenReturn(subjectAuthenticationResult);

        return subjectCreator;
    }
}
