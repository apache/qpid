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

import org.apache.commons.configuration.ConfigurationException;
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

    private void setUpGroupAccessControl() throws ConfigurationException
    {
        configureAccessControl(createGroupRuleSet());
    }

    private void configureAccessControl(final RuleSet rs) throws ConfigurationException
    {
        _plugin = (AccessControl) AccessControl.FACTORY.newInstance(createConfiguration(rs));
        SecurityManager.setThreadSubject(null);
        CurrentActor.set(new TestLogActor(messageLogger));
    }

    private RuleSet createGroupRuleSet()
    {
        final RuleSet rs = new RuleSet();
        rs.addGroup("aclGroup1", Arrays.asList(new String[] {"member1", "Member2"}));

        // Rule expressed with username
        rs.grant(0, "user1", Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        // Rule expressed with a acl group
        rs.grant(1, "aclGroup1", Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        // Rule expressed with an external group
        rs.grant(2, "extGroup1", Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        // Catch all rule
        rs.grant(3, Rule.ALL, Permission.DENY_LOG, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);

        return rs;
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        SecurityManager.setThreadSubject(null);
    }

    /**
     * ACL plugin must always abstain if there is no  subject attached to the thread.
     */
    public void testNoSubjectAlwaysAbstains() throws ConfigurationException
    {
        setUpGroupAccessControl();
        SecurityManager.setThreadSubject(null);

        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ABSTAIN, result);
    }

    /**
     * Tests that an allow rule expressed with a username allows an operation performed by a thread running
     * with the same username.
     */
    public void testUsernameAllowsOperation() throws ConfigurationException
    {
        setUpGroupAccessControl();
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user1"));

        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ALLOWED, result);
    }

    /**
     * Tests that an allow rule expressed with an <b>ACL groupname</b> allows an operation performed by a thread running
     * by a user who belongs to the same group..
     */
    public void testAclGroupMembershipAllowsOperation() throws ConfigurationException
    {
        setUpGroupAccessControl();
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("member1"));

        Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ALLOWED, result);

        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("Member2"));

        result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ALLOWED, result);
    }

    /**
     * Tests that a deny rule expressed with an <b>External groupname</b> denies an operation performed by a thread running
     * by a user who belongs to the same group.
     */
    public void testExternalGroupMembershipDeniesOperation() throws ConfigurationException
    {
        setUpGroupAccessControl();
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user3", "extGroup1"));

        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.DENIED, result);
    }

    /**
     * Tests that the catch all deny denies the operation and logs with the logging actor.
     */
    public void testCatchAllRuleDeniesUnrecognisedUsername() throws ConfigurationException
    {
        setUpGroupAccessControl();
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("unknown", "unkgroup1", "unkgroup2"));

        assertEquals("Expecting zero messages before test", 0, messageLogger.getLogMessages().size());
        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.DENIED, result);

        assertEquals("Expecting one message before test", 1, messageLogger.getLogMessages().size());
        assertTrue("Logged message does not contain expected string", messageLogger.messageContains(0, "ACL-1002"));
    }

    /**
     * Tests that a grant access method rule allows any access operation to be performed on any component
     */
    public void testAuthoriseAccessMethodWhenAllAccessOperationsAllowedOnAllComponents() throws ConfigurationException
    {
        final RuleSet rs = new RuleSet();

        // grant user4 access right on any method in any component
        rs.grant(1, "user4", Permission.ALLOW, Operation.ACCESS, ObjectType.METHOD, new ObjectProperties(ObjectProperties.STAR));
        configureAccessControl(rs);
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user4"));

        ObjectProperties actionProperties = new ObjectProperties("getName");
        actionProperties.put(ObjectProperties.Property.COMPONENT, "Test");

        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, actionProperties);
        assertEquals(Result.ALLOWED, result);
    }

    /**
     * Tests that a grant access method rule allows any access operation to be performed on a specified component
     */
    public void testAuthoriseAccessMethodWhenAllAccessOperationsAllowedOnSpecifiedComponent() throws ConfigurationException
    {
        final RuleSet rs = new RuleSet();

        // grant user5 access right on any methods in "Test" component
        ObjectProperties ruleProperties = new ObjectProperties(ObjectProperties.STAR);
        ruleProperties.put(ObjectProperties.Property.COMPONENT, "Test");
        rs.grant(1, "user5", Permission.ALLOW, Operation.ACCESS, ObjectType.METHOD, ruleProperties);
        configureAccessControl(rs);
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user5"));

        ObjectProperties actionProperties = new ObjectProperties("getName");
        actionProperties.put(ObjectProperties.Property.COMPONENT, "Test");
        Result result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, actionProperties);
        assertEquals(Result.ALLOWED, result);

        actionProperties.put(ObjectProperties.Property.COMPONENT, "Test2");
        result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, actionProperties);
        assertEquals(Result.DEFER, result);
    }

    /**
     * Tests that a grant access method rule allows any access operation to be performed on a specified component
     */
    public void testAuthoriseAccessMethodWhenSpecifiedAccessOperationsAllowedOnSpecifiedComponent() throws ConfigurationException
    {
        final RuleSet rs = new RuleSet();

        // grant user6 access right on "getAttribute" method in "Test" component
        ObjectProperties ruleProperties = new ObjectProperties("getAttribute");
        ruleProperties.put(ObjectProperties.Property.COMPONENT, "Test");
        rs.grant(1, "user6", Permission.ALLOW, Operation.ACCESS, ObjectType.METHOD, ruleProperties);
        configureAccessControl(rs);
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user6"));

        ObjectProperties properties = new ObjectProperties("getAttribute");
        properties.put(ObjectProperties.Property.COMPONENT, "Test");
        Result result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);

        properties.put(ObjectProperties.Property.COMPONENT, "Test2");
        result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.DEFER, result);

        properties = new ObjectProperties("getAttribute2");
        properties.put(ObjectProperties.Property.COMPONENT, "Test");
        result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.DEFER, result);
    }

    /**
     * Tests that granting of all method rights on a method allows a specified operation to be performed on any component
     */
    public void testAuthoriseAccessUpdateMethodWhenAllRightsGrantedOnSpecifiedMethodForAllComponents() throws ConfigurationException
    {
        final RuleSet rs = new RuleSet();

        // grant user8 all rights on method queryNames in all component
        rs.grant(1, "user8", Permission.ALLOW, Operation.ALL, ObjectType.METHOD, new ObjectProperties("queryNames"));
        configureAccessControl(rs);
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user8"));

        ObjectProperties properties = new ObjectProperties();
        properties.put(ObjectProperties.Property.COMPONENT, "Test");
        properties.put(ObjectProperties.Property.NAME, "queryNames");

        Result result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);

        result = _plugin.authorise(Operation.UPDATE, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);

        properties = new ObjectProperties("getAttribute");
        properties.put(ObjectProperties.Property.COMPONENT, "Test");
        result = _plugin.authorise(Operation.UPDATE, ObjectType.METHOD, properties);
        assertEquals(Result.DEFER, result);

        result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.DEFER, result);
    }

    /**
     * Tests that granting of all method rights allows any operation to be performed on any component
     */
    public void testAuthoriseAccessUpdateMethodWhenAllRightsGrantedOnAllMethodsInAllComponents() throws ConfigurationException
    {
        final RuleSet rs = new RuleSet();

        // grant user9 all rights on any method in all component
        rs.grant(1, "user9", Permission.ALLOW, Operation.ALL, ObjectType.METHOD, new ObjectProperties());
        configureAccessControl(rs);
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user9"));

        ObjectProperties properties = new ObjectProperties("queryNames");
        properties.put(ObjectProperties.Property.COMPONENT, "Test");

        Result result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);

        result = _plugin.authorise(Operation.UPDATE, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);

        properties = new ObjectProperties("getAttribute");
        properties.put(ObjectProperties.Property.COMPONENT, "Test");
        result = _plugin.authorise(Operation.UPDATE, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);

        result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);
    }

    /**
     * Tests that granting of access method rights with mask allows matching operations to be performed on the specified component
     */
    public void testAuthoriseAccessMethodWhenMatchingAcessOperationsAllowedOnSpecifiedComponent() throws ConfigurationException
    {
        final RuleSet rs = new RuleSet();

        // grant user9 all rights on "getAttribute*" methods in Test component
        ObjectProperties ruleProperties = new ObjectProperties();
        ruleProperties.put(ObjectProperties.Property.COMPONENT, "Test");
        ruleProperties.put(ObjectProperties.Property.NAME, "getAttribute*");

        rs.grant(1, "user9", Permission.ALLOW, Operation.ACCESS, ObjectType.METHOD, ruleProperties);
        configureAccessControl(rs);
        SecurityManager.setThreadSubject(TestPrincipalUtils.createTestSubject("user9"));

        ObjectProperties properties = new ObjectProperties("getAttributes");
        properties.put(ObjectProperties.Property.COMPONENT, "Test");
        Result result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);

        properties = new ObjectProperties("getAttribute");
        properties.put(ObjectProperties.Property.COMPONENT, "Test");
        result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.ALLOWED, result);

        properties = new ObjectProperties("getAttribut");
        properties.put(ObjectProperties.Property.COMPONENT, "Test");
        result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, properties);
        assertEquals(Result.DEFER, result);
    }

    /**
     * Creates a configuration plugin for the {@link AccessControl} plugin.
     */
    private ConfigurationPlugin createConfiguration(final RuleSet rs)
    {
        final ConfigurationPlugin cp = new ConfigurationPlugin()
        {
            @SuppressWarnings("unchecked")
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
