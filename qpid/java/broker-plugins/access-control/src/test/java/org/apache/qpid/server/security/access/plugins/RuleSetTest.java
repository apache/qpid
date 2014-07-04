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
import static org.mockito.Mockito.when;

import javax.security.auth.Subject;

import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.Permission;
import org.apache.qpid.server.security.access.ObjectProperties.Property;
import org.apache.qpid.server.security.access.config.Rule;
import org.apache.qpid.server.security.access.config.RuleSet;
import org.apache.qpid.server.security.auth.TestPrincipalUtils;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * This test checks that the {@link RuleSet} object which forms the core of the access control plugin performs correctly.
 *
 * The ruleset is configured directly rather than using an external file by adding rules individually, calling the
 * {@link RuleSet#grant(Integer, String, Permission, Operation, ObjectType, ObjectProperties)} method. Then, the
 * access control mechanism is validated by checking whether operations would be authorised by calling the
 * {@link RuleSet#check(Subject, Operation, ObjectType, ObjectProperties)} method.
 *
 * It ensure that permissions can be granted correctly on users directly and on groups.
 */
public class RuleSetTest extends QpidTestCase
{
    private static final String DENIED_VH = "deniedVH";
    private static final String ALLOWED_VH = "allowedVH";

    private RuleSet _ruleSet; // Object under test

    private static final String TEST_USER = "user";

    // Common things that are passed to frame constructors
    private String _queueName = this.getClass().getName() + "queue";
    private String _exchangeName = "amq.direct";
    private String _exchangeType = "direct";
    private Subject _testSubject = TestPrincipalUtils.createTestSubject(TEST_USER);
    private AMQQueue<?> _queue;
    private VirtualHost<?,?,?> _virtualHost;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _ruleSet = new RuleSet(mock(EventLoggerProvider.class));

        _virtualHost = mock(VirtualHost.class);
        _queue = mock(AMQQueue.class);
        when(_queue.getName()).thenReturn(_queueName);
        when(_queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
    }

    @Override
    public void tearDown() throws Exception
    {
        _ruleSet.clear();
        super.tearDown();
    }

    public void assertDenyGrantAllow(Subject subject, Operation operation, ObjectType objectType)
    {
        assertDenyGrantAllow(subject, operation, objectType, ObjectProperties.EMPTY);
    }

    public void assertDenyGrantAllow(Subject subject, Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        assertEquals(Result.DENIED, _ruleSet.check(subject, operation, objectType, properties));
        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, operation, objectType, properties);
        assertEquals(1, _ruleSet.getRuleCount());
        assertEquals(Result.ALLOWED, _ruleSet.check(subject, operation, objectType, properties));
    }

    public void testEmptyRuleSet()
    {
        assertNotNull(_ruleSet);
        assertEquals(_ruleSet.getRuleCount(), 0);
        assertEquals(_ruleSet.getDefault(), _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
    }

    public void testVirtualHostNodeCreateAllowPermissionWithVirtualHostName() throws Exception
    {
        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.VIRTUALHOSTNODE, ObjectProperties.EMPTY);
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.VIRTUALHOSTNODE, ObjectProperties.EMPTY));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.DELETE, ObjectType.VIRTUALHOSTNODE, ObjectProperties.EMPTY));
    }

    public void testVirtualHostAccessAllowPermissionWithVirtualHostName() throws Exception
    {
        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ALLOWED_VH));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ALLOWED_VH)));
        assertEquals(Result.DEFER, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(DENIED_VH)));
    }

    public void testVirtualHostAccessAllowPermissionWithNameSetToWildCard() throws Exception
    {
        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ObjectProperties.WILD_CARD));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ALLOWED_VH)));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(DENIED_VH)));
    }

    public void testVirtualHostAccessAllowPermissionWithNoName() throws Exception
    {
        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ALLOWED_VH)));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(DENIED_VH)));
    }

    public void testVirtualHostAccessDenyPermissionWithNoName() throws Exception
    {
        _ruleSet.grant(0, TEST_USER, Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ALLOWED_VH)));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(DENIED_VH)));
    }

    public void testVirtualHostAccessDenyPermissionWithNameSetToWildCard() throws Exception
    {
        _ruleSet.grant(0, TEST_USER, Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ObjectProperties.WILD_CARD));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ALLOWED_VH)));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(DENIED_VH)));
    }

    public void testVirtualHostAccessAllowDenyPermissions() throws Exception
    {
        _ruleSet.grant(0, TEST_USER, Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(DENIED_VH));
        _ruleSet.grant(1, TEST_USER, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ALLOWED_VH));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(ALLOWED_VH)));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(DENIED_VH)));
    }

    public void testVirtualHostAccessAllowPermissionWithVirtualHostNameOtherPredicate() throws Exception
    {
        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.VIRTUALHOST_NAME, ALLOWED_VH);

        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, properties);
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, properties));
        assertEquals(Result.DEFER, _ruleSet.check(_testSubject, Operation.ACCESS, ObjectType.VIRTUALHOST, new ObjectProperties(DENIED_VH)));
    }


    public void testQueueCreateNamed() throws Exception
    {
        assertDenyGrantAllow(_testSubject, Operation.CREATE, ObjectType.QUEUE, new ObjectProperties(_queueName));
    }

    public void testQueueCreateNamedVirtualHost() throws Exception
    {
        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, new ObjectProperties(Property.VIRTUALHOST_NAME, ALLOWED_VH));

        when(_virtualHost.getName()).thenReturn(ALLOWED_VH);
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, new ObjectProperties(_queue)));

        when(_virtualHost.getName()).thenReturn(DENIED_VH);
        assertEquals(Result.DEFER, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, new ObjectProperties(_queue)));
    }

    public void testQueueCreateNamedNullRoutingKey()
    {
        ObjectProperties properties = new ObjectProperties(_queueName);
        properties.put(ObjectProperties.Property.ROUTING_KEY, (String) null);

        assertDenyGrantAllow(_testSubject, Operation.CREATE, ObjectType.QUEUE, properties);
    }

    public void testExchangeCreateNamedVirtualHost()
    {
        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.EXCHANGE, new ObjectProperties(Property.VIRTUALHOST_NAME, ALLOWED_VH));

        ExchangeImpl<?> exchange = mock(ExchangeImpl.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getType()).thenReturn(_exchangeType);
        when(_virtualHost.getName()).thenReturn(ALLOWED_VH);

        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.EXCHANGE, new ObjectProperties(exchange)));

        when(_virtualHost.getName()).thenReturn(DENIED_VH);
        assertEquals(Result.DEFER, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.EXCHANGE, new ObjectProperties(exchange)));
    }

    public void testExchangeCreate()
    {
        ObjectProperties properties = new ObjectProperties(_exchangeName);
        properties.put(ObjectProperties.Property.TYPE, _exchangeType);

        assertDenyGrantAllow(_testSubject, Operation.CREATE, ObjectType.EXCHANGE, properties);
    }

    public void testConsume()
    {
        assertDenyGrantAllow(_testSubject, Operation.CONSUME, ObjectType.QUEUE);
    }

    public void testPublish()
    {
        assertDenyGrantAllow(_testSubject, Operation.PUBLISH, ObjectType.EXCHANGE);
    }

    /**
    * If the consume permission for temporary queues is for an unnamed queue then it should
    * be global for any temporary queue but not for any non-temporary queue
    */
    public void testTemporaryUnnamedQueueConsume()
    {
        ObjectProperties temporary = new ObjectProperties();
        temporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);

        ObjectProperties normal = new ObjectProperties();
        normal.put(ObjectProperties.Property.AUTO_DELETE, Boolean.FALSE);

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, temporary));
        _ruleSet.grant(0, TEST_USER, Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, temporary);
        assertEquals(1, _ruleSet.getRuleCount());
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, temporary));

        // defer to global if exists, otherwise default answer - this is handled by the security manager
        assertEquals(Result.DEFER, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, normal));
    }

    /**
     * Test that temporary queue permissions before queue perms in the ACL config work correctly
     */
    public void testTemporaryQueueFirstConsume()
    {
        ObjectProperties temporary = new ObjectProperties(_queueName);
        temporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);

        ObjectProperties normal = new ObjectProperties(_queueName);
        normal.put(ObjectProperties.Property.AUTO_DELETE, Boolean.FALSE);

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, temporary));

        // should not matter if the temporary permission is processed first or last
        _ruleSet.grant(1, TEST_USER, Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, normal);
        _ruleSet.grant(2, TEST_USER, Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, temporary);
        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, normal));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, temporary));
    }

    /**
     * Test that temporary queue permissions after queue perms in the ACL config work correctly
     */
    public void testTemporaryQueueLastConsume()
    {
        ObjectProperties temporary = new ObjectProperties(_queueName);
        temporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);

        ObjectProperties normal = new ObjectProperties(_queueName);
        normal.put(ObjectProperties.Property.AUTO_DELETE, Boolean.FALSE);

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, temporary));

        // should not matter if the temporary permission is processed first or last
        _ruleSet.grant(1, TEST_USER, Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, temporary);
        _ruleSet.grant(2, TEST_USER, Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, normal);
        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, normal));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CONSUME, ObjectType.QUEUE, temporary));
    }

    /*
     * Test different rules for temporary queues.
     */

    /**
     * The more generic rule first is used, so both requests are allowed.
     */
    public void testFirstNamedSecondTemporaryQueueDenied()
    {
        ObjectProperties named = new ObjectProperties(_queueName);
        ObjectProperties namedTemporary = new ObjectProperties(_queueName);
        namedTemporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));

        _ruleSet.grant(1, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, named);
        _ruleSet.grant(2, TEST_USER, Permission.DENY, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));
    }

    /**
     * The more specific rule is first, so those requests are denied.
     */
    public void testFirstTemporarySecondNamedQueueDenied()
    {
        ObjectProperties named = new ObjectProperties(_queueName);
        ObjectProperties namedTemporary = new ObjectProperties(_queueName);
        namedTemporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));

        _ruleSet.grant(1, TEST_USER, Permission.DENY, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        _ruleSet.grant(2, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, named);
        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));
    }

    /**
     * The more specific rules are first, so those requests are denied.
     */
    public void testFirstTemporarySecondDurableThirdNamedQueueDenied()
    {
        ObjectProperties named = new ObjectProperties(_queueName);
        ObjectProperties namedTemporary = new ObjectProperties(_queueName);
        namedTemporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);
        ObjectProperties namedDurable = new ObjectProperties(_queueName);
        namedDurable.put(ObjectProperties.Property.DURABLE, Boolean.TRUE);

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedDurable));

        _ruleSet.grant(1, TEST_USER, Permission.DENY, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        _ruleSet.grant(2, TEST_USER, Permission.DENY, Operation.CREATE, ObjectType.QUEUE, namedDurable);
        _ruleSet.grant(3, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, named);
        assertEquals(3, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedDurable));
    }

    public void testNamedTemporaryQueueAllowed()
    {
        ObjectProperties named = new ObjectProperties(_queueName);
        ObjectProperties namedTemporary = new ObjectProperties(_queueName);
        namedTemporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));

        _ruleSet.grant(1, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        _ruleSet.grant(2, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, named);
        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));
    }

    public void testNamedTemporaryQueueDeniedAllowed()
    {
        ObjectProperties named = new ObjectProperties(_queueName);
        ObjectProperties namedTemporary = new ObjectProperties(_queueName);
        namedTemporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));

        _ruleSet.grant(1, TEST_USER, Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        _ruleSet.grant(2, TEST_USER, Permission.DENY, Operation.CREATE, ObjectType.QUEUE, named);
        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.DENIED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.ALLOWED, _ruleSet.check(_testSubject, Operation.CREATE, ObjectType.QUEUE, namedTemporary));
    }

    /**
     * Tests support for the {@link Rule#ALL} keyword.
     */
    public void testAllowToAll()
    {
        _ruleSet.grant(1, Rule.ALL, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(1, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(TestPrincipalUtils.createTestSubject("usera"),Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
        assertEquals(Result.ALLOWED, _ruleSet.check(TestPrincipalUtils.createTestSubject("userb"),Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
    }

    public void testGroupsSupported()
    {
        String allowGroup = "allowGroup";
        String deniedGroup = "deniedGroup";

        _ruleSet.grant(1, allowGroup, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        _ruleSet.grant(2, deniedGroup, Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);

        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(TestPrincipalUtils.createTestSubject("usera", allowGroup),Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
        assertEquals(Result.DENIED, _ruleSet.check(TestPrincipalUtils.createTestSubject("userb", deniedGroup),Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
        assertEquals(Result.DEFER, _ruleSet.check(TestPrincipalUtils.createTestSubject("user", "group not mentioned in acl"),Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
    }

    /**
     * Rule order in the ACL determines the outcome of the check.  This test ensures that a user who is
     * granted explicit permission on an object, is granted that access even though a group
     * to which the user belongs is later denied the permission.
     */
    public void testAllowDeterminedByRuleOrder()
    {
        String group = "group";
        String user = "user";

        _ruleSet.grant(1, user, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        _ruleSet.grant(2, group, Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.ALLOWED, _ruleSet.check(TestPrincipalUtils.createTestSubject(user, group),Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
    }

    /**
     * Rule order in the ACL determines the outcome of the check.  This tests ensures that a user who is denied
     * access by group, is denied access, despite there being a later rule granting permission to that user.
     */
    public void testDenyDeterminedByRuleOrder()
    {
        String group = "aclgroup";
        String user = "usera";

        _ruleSet.grant(1, group, Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        _ruleSet.grant(2, user, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);

        assertEquals(2, _ruleSet.getRuleCount());

        assertEquals(Result.DENIED, _ruleSet.check(TestPrincipalUtils.createTestSubject(user, group),Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
    }

    public void testUserInMultipleGroups()
    {
        String allowedGroup = "group1";
        String deniedGroup = "group2";

        _ruleSet.grant(1, allowedGroup, Permission.ALLOW, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);
        _ruleSet.grant(2, deniedGroup, Permission.DENY, Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY);

        Subject subjectInBothGroups = TestPrincipalUtils.createTestSubject("user", allowedGroup, deniedGroup);
        Subject subjectInDeniedGroupAndOneOther = TestPrincipalUtils.createTestSubject("user", deniedGroup, "some other group");
        Subject subjectInAllowedGroupAndOneOther = TestPrincipalUtils.createTestSubject("user", allowedGroup, "some other group");

        assertEquals(Result.ALLOWED, _ruleSet.check(subjectInBothGroups,Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));

        assertEquals(Result.DENIED, _ruleSet.check(subjectInDeniedGroupAndOneOther,Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));

        assertEquals(Result.ALLOWED, _ruleSet.check(subjectInAllowedGroupAndOneOther,Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
    }
}
