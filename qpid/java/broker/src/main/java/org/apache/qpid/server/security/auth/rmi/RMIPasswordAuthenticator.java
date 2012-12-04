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

import java.net.SocketAddress;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;

public class RMIPasswordAuthenticator implements JMXAuthenticator
{
    static final String UNABLE_TO_LOOKUP = "The broker was unable to lookup the user details";
    static final String SHOULD_BE_STRING_ARRAY = "User details should be String[]";
    static final String SHOULD_HAVE_2_ELEMENTS = "User details should have 2 elements, username, password";
    static final String SHOULD_BE_NON_NULL = "Supplied username and password should be non-null";
    static final String INVALID_CREDENTIALS = "Invalid user details supplied";
    static final String USER_NOT_AUTHORISED_FOR_MANAGEMENT = "User not authorised for management";
    static final String CREDENTIALS_REQUIRED = "User details are required. " +
                        "Please ensure you are using an up to date management console to connect.";

    private final Broker _broker;
    private final SocketAddress _address;

    public RMIPasswordAuthenticator(Broker broker, SocketAddress address)
    {
        _broker = broker;
        _address = address;
    }

    public Subject authenticate(Object credentials) throws SecurityException
    {
        validateCredentials(credentials);

        final String[] userCredentials = (String[]) credentials;
        final String username = (String) userCredentials[0];
        final String password = (String) userCredentials[1];

        final Subject authenticatedSubject = doAuthentication(username, password);
        doManagementAuthorisation(authenticatedSubject);
        return authenticatedSubject;
    }

    private void validateCredentials(Object credentials)
    {
        // Verify that credential's are of type String[].
        if (!(credentials instanceof String[]))
        {
            if (credentials == null)
            {
                throw new SecurityException(CREDENTIALS_REQUIRED);
            }
            else
            {
                throw new SecurityException(SHOULD_BE_STRING_ARRAY);
            }
        }

        // Verify that required number of credentials.
        if (((String[])credentials).length != 2)
        {
            throw new SecurityException(SHOULD_HAVE_2_ELEMENTS);
        }
    }

    private Subject doAuthentication(final String username, final String password)
    {
        // Verify that all required credentials are actually present.
        if (username == null || password == null)
        {
            throw new SecurityException(SHOULD_BE_NON_NULL);
        }

        SubjectCreator subjectCreator = _broker.getSubjectCreator(_address);
        if (subjectCreator == null)
        {
            throw new SecurityException("Can't get subject creator for " + _address);
        }

        final SubjectAuthenticationResult result = subjectCreator.authenticate(username, password);

        if (AuthenticationStatus.ERROR.equals(result.getStatus()))
        {
            throw new SecurityException("Authentication manager failed", result.getCause());
        }
        else if (AuthenticationStatus.SUCCESS.equals(result.getStatus()))
        {
            return result.getSubject();
        }
        else
        {
            throw new SecurityException(INVALID_CREDENTIALS);
        }
    }

    private void doManagementAuthorisation(Subject authenticatedSubject)
    {
        SecurityManager.setThreadSubject(authenticatedSubject);
        try
        {
            if (!_broker.getSecurityManager().accessManagement())
            {
                throw new SecurityException(USER_NOT_AUTHORISED_FOR_MANAGEMENT);
            }
        }
        finally
        {
            SecurityManager.setThreadSubject(null);
        }
    }


}