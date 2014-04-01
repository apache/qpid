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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;

import junit.framework.TestCase;

import org.apache.qpid.server.connection.ConnectionPrincipal;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.logging.UnitTestMessageLogger;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.Permission;
import org.apache.qpid.server.security.access.config.Rule;
import org.apache.qpid.server.security.access.config.RuleSet;
import org.apache.qpid.server.security.auth.TestPrincipalUtils;

/**
 * In these tests, the ruleset is configured programmatically rather than from an external file.
 *
 * @see RuleSetTest
 */
public class DefaultAccessControlTest extends TestCase
{
    private static final String ALLOWED_GROUP = "allowed_group";
    private static final String DENIED_GROUP = "denied_group";

    private DefaultAccessControl _plugin = null;  // Class under test
    private UnitTestMessageLogger _messageLogger;
    private EventLogger _eventLogger;

    public void setUp() throws Exception
    {
        super.setUp();
        _messageLogger = new UnitTestMessageLogger();
        _eventLogger = new EventLogger(_messageLogger);
        _plugin = null;
    }

    private void setUpGroupAccessControl()
    {
        configureAccessControl(createGroupRuleSet());
    }

    private void configureAccessControl(final RuleSet rs)
    {
        _plugin = new DefaultAccessControl(rs);
    }

    private RuleSet createGroupRuleSet()
    {
        final EventLoggerProvider provider = mock(EventLoggerProvider.class);
        when(provider.getEventLogger()).thenReturn(_eventLogger);
        final RuleSet rs = new RuleSet(provider);

        // Rule expressed with username
        rs.grant(0, "user1", Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        // Rules expressed with groups
        rs.grant(1, ALLOWED_GROUP, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        rs.grant(2, DENIED_GROUP, Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        // Catch all rule
        rs.grant(3, Rule.ALL, Permission.DENY_LOG, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);

        return rs;
    }

    /**
     * ACL plugin must always abstain if there is no  subject attached to the thread.
     */
    public void testNoSubjectAlwaysAbstains()
    {
        setUpGroupAccessControl();
        final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ABSTAIN, result);
    }

    /**
     * Tests that an allow rule expressed with a username allows an operation performed by a thread running
     * with the same username.
     */
    public void testUsernameAllowsOperation()
    {
        setUpGroupAccessControl();
        Subject.doAs(TestPrincipalUtils.createTestSubject("user1"), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
                assertEquals(Result.ALLOWED, result);
                return null;
            }
        });
    }

    /**
     * Tests that an allow rule expressed with an <b>ACL groupname</b> allows an operation performed by a thread running
     * by a user who belongs to the same group..
     */
    public void testGroupMembershipAllowsOperation()
    {
        setUpGroupAccessControl();

        authoriseAndAssertResult(Result.ALLOWED, "member of allowed group", ALLOWED_GROUP);
        authoriseAndAssertResult(Result.DENIED, "member of denied group", DENIED_GROUP);
        authoriseAndAssertResult(Result.ALLOWED, "another member of allowed group", ALLOWED_GROUP);
    }

    /**
     * Tests that a deny rule expressed with a <b>groupname</b> denies an operation performed by a thread running
     * by a user who belongs to the same group.
     */
    public void testGroupMembershipDeniesOperation()
    {
        setUpGroupAccessControl();
        authoriseAndAssertResult(Result.DENIED, "user3", DENIED_GROUP);
    }

    /**
     * Tests that the catch all deny denies the operation and logs with the logging actor.
     */
    public void testCatchAllRuleDeniesUnrecognisedUsername()
    {
        setUpGroupAccessControl();
        Subject.doAs(TestPrincipalUtils.createTestSubject("unknown", "unkgroup1", "unkgroup2"),
                     new PrivilegedAction<Object>()
                     {
                         @Override
                         public Object run()
                         {
                             assertEquals("Expecting zero messages before test",
                                          0,
                                          _messageLogger.getLogMessages().size());
                             final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
                             assertEquals(Result.DENIED, result);

                             assertEquals("Expecting one message before test", 1, _messageLogger.getLogMessages().size());
                             assertTrue("Logged message does not contain expected string",
                                        _messageLogger.messageContains(0, "ACL-1002"));
                             return null;
                         }
                     });

    }

    /**
     * Tests that a grant access method rule allows any access operation to be performed on any component
     */
    public void testAuthoriseAccessMethodWhenAllAccessOperationsAllowedOnAllComponents()
    {
        final RuleSet rs = new RuleSet(mock(EventLoggerProvider.class));

        // grant user4 access right on any method in any component
        rs.grant(1, "user4", Permission.ALLOW, Operation.ACCESS, ObjectType.METHOD, new ObjectProperties(ObjectProperties.WILD_CARD));
        configureAccessControl(rs);
        Subject.doAs(TestPrincipalUtils.createTestSubject("user4"), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                ObjectProperties actionProperties = new ObjectProperties("getName");
                actionProperties.put(ObjectProperties.Property.COMPONENT, "Test");

                final Result result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, actionProperties);
                assertEquals(Result.ALLOWED, result);
                return null;
            }
        });

    }

    /**
     * Tests that a grant access method rule allows any access operation to be performed on a specified component
     */
    public void testAuthoriseAccessMethodWhenAllAccessOperationsAllowedOnSpecifiedComponent()
    {
        final RuleSet rs = new RuleSet(mock(EventLoggerProvider.class));

        // grant user5 access right on any methods in "Test" component
        ObjectProperties ruleProperties = new ObjectProperties(ObjectProperties.WILD_CARD);
        ruleProperties.put(ObjectProperties.Property.COMPONENT, "Test");
        rs.grant(1, "user5", Permission.ALLOW, Operation.ACCESS, ObjectType.METHOD, ruleProperties);
        configureAccessControl(rs);
        Subject.doAs(TestPrincipalUtils.createTestSubject("user5"), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                ObjectProperties actionProperties = new ObjectProperties("getName");
                actionProperties.put(ObjectProperties.Property.COMPONENT, "Test");
                Result result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, actionProperties);
                assertEquals(Result.ALLOWED, result);

                actionProperties.put(ObjectProperties.Property.COMPONENT, "Test2");
                result = _plugin.authorise(Operation.ACCESS, ObjectType.METHOD, actionProperties);
                assertEquals(Result.DEFER, result);
                return null;
            }
        });


    }

    public void testAccess() throws Exception
    {
        final Subject subject = TestPrincipalUtils.createTestSubject("user1");
        final String testVirtualHost = getName();
        final InetAddress inetAddress = InetAddress.getLocalHost();
        final InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, 1);

        AMQConnectionModel connectionModel = mock(AMQConnectionModel.class);
        when(connectionModel.getRemoteAddress()).thenReturn(inetSocketAddress);

        subject.getPrincipals().add(new ConnectionPrincipal(connectionModel));

        Subject.doAs(subject, new PrivilegedExceptionAction<Object>()
        {
            @Override
            public Object run() throws Exception
            {
                RuleSet mockRuleSet = mock(RuleSet.class);

                DefaultAccessControl accessControl = new DefaultAccessControl(mockRuleSet);

                ObjectProperties properties = new ObjectProperties(testVirtualHost);
                accessControl.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, properties);

                verify(mockRuleSet).check(subject, Operation.ACCESS, ObjectType.VIRTUALHOST, properties, inetAddress);
                return null;
            }
        });

    }

    public void testAccessIsDeniedIfRuleThrowsException() throws Exception
    {
        final Subject subject = TestPrincipalUtils.createTestSubject("user1");
        final InetAddress inetAddress = InetAddress.getLocalHost();
        final InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, 1);

        AMQConnectionModel connectionModel = mock(AMQConnectionModel.class);
        when(connectionModel.getRemoteAddress()).thenReturn(inetSocketAddress);

        subject.getPrincipals().add(new ConnectionPrincipal(connectionModel));

        Subject.doAs(subject, new PrivilegedExceptionAction<Object>()
        {
            @Override
            public Object run() throws Exception
            {


                RuleSet mockRuleSet = mock(RuleSet.class);
                when(mockRuleSet.check(
                        subject,
                        Operation.ACCESS,
                        ObjectType.VIRTUALHOST,
                        ObjectProperties.EMPTY,
                        inetAddress)).thenThrow(new RuntimeException());

                DefaultAccessControl accessControl = new DefaultAccessControl(mockRuleSet);
                Result result = accessControl.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);

                assertEquals(Result.DENIED, result);
                return null;
            }
        });

    }


    /**
     * Tests that a grant access method rule allows any access operation to be performed on a specified component
     */
    public void testAuthoriseAccessMethodWhenSpecifiedAccessOperationsAllowedOnSpecifiedComponent()
    {
        final RuleSet rs = new RuleSet(mock(EventLoggerProvider.class));

        // grant user6 access right on "getAttribute" method in "Test" component
        ObjectProperties ruleProperties = new ObjectProperties("getAttribute");
        ruleProperties.put(ObjectProperties.Property.COMPONENT, "Test");
        rs.grant(1, "user6", Permission.ALLOW, Operation.ACCESS, ObjectType.METHOD, ruleProperties);
        configureAccessControl(rs);
        Subject.doAs(TestPrincipalUtils.createTestSubject("user6"), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
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

                return null;
            }
        });

    }

    /**
     * Tests that granting of all method rights on a method allows a specified operation to be performed on any component
     */
    public void testAuthoriseAccessUpdateMethodWhenAllRightsGrantedOnSpecifiedMethodForAllComponents()
    {
        final RuleSet rs = new RuleSet(mock(EventLoggerProvider.class));

        // grant user8 all rights on method queryNames in all component
        rs.grant(1, "user8", Permission.ALLOW, Operation.ALL, ObjectType.METHOD, new ObjectProperties("queryNames"));
        configureAccessControl(rs);
        Subject.doAs(TestPrincipalUtils.createTestSubject("user8"), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
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
                return null;
            }
        });


    }

    /**
     * Tests that granting of all method rights allows any operation to be performed on any component
     */
    public void testAuthoriseAccessUpdateMethodWhenAllRightsGrantedOnAllMethodsInAllComponents()
    {
        final RuleSet rs = new RuleSet(mock(EventLoggerProvider.class));

        // grant user9 all rights on any method in all component
        rs.grant(1, "user9", Permission.ALLOW, Operation.ALL, ObjectType.METHOD, new ObjectProperties());
        configureAccessControl(rs);
        Subject.doAs(TestPrincipalUtils.createTestSubject("user9"), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
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
                return null;
            }
        });


    }

    /**
     * Tests that granting of access method rights with mask allows matching operations to be performed on the specified component
     */
    public void testAuthoriseAccessMethodWhenMatchingAccessOperationsAllowedOnSpecifiedComponent()
    {
        final RuleSet rs = new RuleSet(mock(EventLoggerProvider.class));

        // grant user9 all rights on "getAttribute*" methods in Test component
        ObjectProperties ruleProperties = new ObjectProperties();
        ruleProperties.put(ObjectProperties.Property.COMPONENT, "Test");
        ruleProperties.put(ObjectProperties.Property.NAME, "getAttribute*");

        rs.grant(1, "user9", Permission.ALLOW, Operation.ACCESS, ObjectType.METHOD, ruleProperties);
        configureAccessControl(rs);
        Subject.doAs(TestPrincipalUtils.createTestSubject("user9"), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
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
                return null;
            }
        });
    }

    private void authoriseAndAssertResult(final Result expectedResult, String userName, String... groups)
    {

        Subject.doAs(TestPrincipalUtils.createTestSubject(userName, groups), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                Result result = _plugin.authorise(Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
                assertEquals(expectedResult, result);
                return null;
            }
        });

    }
}
