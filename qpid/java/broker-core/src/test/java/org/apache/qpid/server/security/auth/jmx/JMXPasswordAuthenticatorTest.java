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
package org.apache.qpid.server.security.auth.jmx;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.security.Principal;

import javax.security.auth.Subject;

import junit.framework.TestCase;

import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.security.SecurityManager;

/**
 * Tests the JMXPasswordAuthenticator and its collaboration with the AuthenticationManager.
 *
 */
public class JMXPasswordAuthenticatorTest extends TestCase
{
    static final String USER_NOT_AUTHORISED_FOR_MANAGEMENT = "User not authorised for management";
    private static final String USERNAME = "guest";
    private static final String PASSWORD = "password";

    private final SecurityManager _securityManager = mock(SecurityManager.class);
    private final Subject _loginSubject = new Subject();
    private final String[] _credentials = new String[] {USERNAME, PASSWORD};

    private JMXPasswordAuthenticator _rmipa;

    private SubjectCreator _usernamePasswordOkaySubjectCreator = createMockSubjectCreator(true, null);
    private SubjectCreator _badPasswordSubjectCreator = createMockSubjectCreator(false, null);

    /**
     * Tests a successful authentication.  Ensures that the expected subject is returned.
     */
    public void testAuthenticationSuccess()
    {
        _rmipa = new JMXPasswordAuthenticator(_usernamePasswordOkaySubjectCreator, _securityManager);

        Subject newSubject = _rmipa.authenticate(_credentials);
        assertSame("Subject must be unchanged", _loginSubject, newSubject);
    }

    /**
     * Tests a unsuccessful authentication.
     */
    public void testUsernameOrPasswordInvalid()
    {
        _rmipa = new JMXPasswordAuthenticator(_badPasswordSubjectCreator, _securityManager);

        try
        {
            _rmipa.authenticate(_credentials);
            fail("Exception not thrown");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    JMXPasswordAuthenticator.INVALID_CREDENTIALS, se.getMessage());
        }
    }

    public void testAuthorisationFailure()
    {
        _rmipa = new JMXPasswordAuthenticator(_usernamePasswordOkaySubjectCreator, _securityManager);
        doThrow(new AccessControlException(USER_NOT_AUTHORISED_FOR_MANAGEMENT)).when(_securityManager).accessManagement();

        try
        {
            _rmipa.authenticate(_credentials);
            fail("Exception not thrown");
        }
        catch (SecurityException se)
        {
            assertEquals("Unexpected exception message",
                    USER_NOT_AUTHORISED_FOR_MANAGEMENT, se.getMessage());
        }
    }

    public void testSubjectCreatorInternalFailure()
    {
        final Exception mockAuthException = new Exception("Mock Auth system failure");
        SubjectCreator subjectCreator = createMockSubjectCreator(false, mockAuthException);
        _rmipa = new JMXPasswordAuthenticator(subjectCreator, _securityManager);

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
