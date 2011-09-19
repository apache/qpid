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
package org.apache.qpid.server.security.access.plugins;

import java.util.Arrays;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.logging.UnitTestMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.Permission;
import org.apache.qpid.server.security.access.config.Rule;
import org.apache.qpid.server.security.access.config.RuleSet;
import org.apache.qpid.server.security.auth.sasl.TestPrincipalUtils;

/**
 * Unit test for ACL V2 plugin.  
 * 
 * This unit test tests the AccessControl class and it collaboration with {@link RuleSet},
 * {@link SecurityManager} and {@link CurrentActor}.   The ruleset is configured programmatically,
 * rather than from an external file.
 * 
 * @see RuleSetTest
 */
public class AccessControlTest extends TestCase
{
    private AccessControl _plugin = null;  // Class under test
    private final UnitTestMessageLogger messageLogger = new UnitTestMessageLogger();

    protected void setUp() throws Exception
    {
        super.setUp();

        final RuleSet rs = new RuleSet();
        rs.addGroup("aclGroup1", Arrays.asList(new String[] {"member1", "member2"}));

        // Rule expressed with username
        rs.grant(0, "user1", Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        // Rule expressed with a acl group
        rs.grant(1, "aclGroup1", Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        // Rule expressed with an external group
        rs.grant(2, "extGroup1", Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        // Catch all rule
        rs.grant(3, Rule.ALL, Permission.DENY_LOG, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);

        _plugin = (AccessControl) AccessControl.FACTORY.newInstance(createConfiguration(rs));

        SecurityManager.setThreadSubject(null);
        
        CurrentActor.set(new TestLogActor(messageLogger));
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        SecurityManager.setThreadSubject(null);
    }

    /** 
     * ACL plugin must always abstain if there is no  subject attached to the thread.
     */
    public void testNoSubjectAlwaysAbstains()
    {
        SecurityManager.setThreadSubject(null);

        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ABSTAIN, result);
    }

    /** 
     * Tests that an allow rule expressed with a username allows an operation performed by a thread running
     * with the same username.
     */
    public void testUsernameAllowsOperation()
    {
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user1"));

        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ALLOWED, result);
    }

    /** 
     * Tests that an allow rule expressed with an <b>ACL groupname</b> allows an operation performed by a thread running
     * by a user who belongs to the same group..
     */
    public void testAclGroupMembershipAllowsOperation()
    {
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("member1"));

        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ALLOWED, result);
    }

    /** 
     * Tests that a deny rule expressed with an <b>External groupname</b> denies an operation performed by a thread running
     * by a user who belongs to the same group.
     */
    public void testExternalGroupMembershipDeniesOperation()
    {
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user3", "extGroup1"));
        
        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.DENIED, result);
    }

    /** 
     * Tests that the catch all deny denies the operation and logs with the logging actor.
     */
    public void testCatchAllRuleDeniesUnrecognisedUsername()
    {
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("unknown", "unkgroup1", "unkgroup2"));
        
        assertEquals("Expecting zero messages before test", 0, messageLogger.getLogMessages().size());
        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.DENIED, result);
        
        assertEquals("Expecting one message before test", 1, messageLogger.getLogMessages().size());
        assertTrue("Logged message does not contain expected string", messageLogger.messageContains(0, "ACL-1002"));
    }
    
    /**
     * Creates a configuration plugin for the {@link AccessControl} plugin.
     */
    private ConfigurationPlugin createConfiguration(final RuleSet rs)
    {
        final ConfigurationPlugin cp = new ConfigurationPlugin()
        {
            public AccessControlConfiguration  getConfiguration(final String plugin)
            {
                return new AccessControlConfiguration()
                {
                    public RuleSet getRuleSet()
                    {
                        return rs;
                    }
                };
            }

            public String[] getElementsProcessed()
            {
                throw new UnsupportedOperationException();
            }
        };

        return cp;
    }
}
