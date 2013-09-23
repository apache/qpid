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
package org.apache.qpid.server.logging.actors;

import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collections;

import javax.security.auth.Subject;

import org.apache.qpid.server.logging.NullRootMessageLogger;
import org.apache.qpid.server.security.auth.TestPrincipalUtils;
import org.apache.qpid.test.utils.QpidTestCase;

public class AbstractManagementActorTest extends QpidTestCase
{
    private AbstractManagementActor _logActor;

    @Override
    public void setUp()
    {
        _logActor = new AbstractManagementActor(new NullRootMessageLogger(), AbstractManagementActor.UNKNOWN_PRINCIPAL)
        {
            @Override
            public String getLogMessage()
            {
                return null;
            }
        };
    }

    public void testGetPrincipalName()
    {
        Subject subject = TestPrincipalUtils.createTestSubject("guest");
        
        final String principalName = Subject.doAs(subject, 
                new PrivilegedAction<String>()
                {
                    public String run()
                    {
                        return _logActor.getPrincipalName();
                    }
                });

        assertEquals("guest", principalName);
    }

    public void testGetPrincipalNameUsingSubjectWithoutAuthenticatedPrincipal()
    {
        Subject subject = new Subject(true, Collections.<Principal>emptySet(), Collections.emptySet(), Collections.emptySet());

        final String principalName = Subject.doAs(subject, 
                new PrivilegedAction<String>()
                {
                    public String run()
                    {
                        return _logActor.getPrincipalName();
                    }
                });

        assertEquals(AbstractManagementActor.UNKNOWN_PRINCIPAL, principalName);
    }

    public void testGetPrincipalWithoutSubject()
    {
        assertEquals(AbstractManagementActor.UNKNOWN_PRINCIPAL, _logActor.getPrincipalName());
    }
}
