/*
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
package org.apache.qpid.server.management.plugin.session;

import static org.mockito.Mockito.verify;
import static org.mockito.Matchers.argThat;

import javax.security.auth.Subject;

import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import junit.framework.TestCase;

public class LoginLogoutReporterTest extends TestCase
{
    private LoginLogoutReporter _loginLogoutReport;
    private Subject _subject = new Subject();
    private LogActor _logActor = Mockito.mock(LogActor.class);

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _subject.getPrincipals().add(new AuthenticatedPrincipal("mockusername"));
        _loginLogoutReport = new LoginLogoutReporter(_logActor, _subject);
    }

    public void testLoginLogged()
    {
        _loginLogoutReport.valueBound(null);
        verify(_logActor).message(isLogMessageWithMessage("MNG-1007 : Open : User mockusername"));
    }

    public void testLogoutLogged()
    {
        _loginLogoutReport.valueUnbound(null);
        verify(_logActor).message(isLogMessageWithMessage("MNG-1008 : Close : User mockusername"));
    }

    private LogMessage isLogMessageWithMessage(final String expectedMessage)
    {
        return argThat( new ArgumentMatcher<LogMessage>()
        {
            @Override
            public boolean matches(Object argument)
            {
                LogMessage actual = (LogMessage) argument;
                return actual.toString().equals(expectedMessage);
            }
        });
    }
}
