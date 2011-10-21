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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.Permission;
import org.apache.qpid.server.security.access.config.RuleSet;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * This test checks that the {@link RuleSet} object which forms the core of the access control plugin performs correctly.
 * 
 * The ruleset is configured directly rather than using an external file by adding rules individually, calling the
 * {@link RuleSet#grant(Integer, String, Permission, Operation, ObjectType, ObjectProperties)} method. Then, the
 * access control mechanism is validated by checking whether operations would be authorised by calling the
 * {@link RuleSet#check(String, Operation, ObjectType, ObjectProperties)} method.
 */
public class RuleSetTest extends QpidTestCase
{
    private RuleSet _ruleSet;

    // Common things that are passed to frame constructors
    private AMQShortString _queueName = new AMQShortString(this.getClass().getName() + "queue");
    private AMQShortString _exchangeName = new AMQShortString("amq.direct");
    private AMQShortString _exchangeType = new AMQShortString("direct");

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _ruleSet = new RuleSet();
        _ruleSet.configure(RuleSet.TRANSITIVE, Boolean.FALSE);
    }

    @Override
    public void tearDown() throws Exception
    {
        _ruleSet.clear();
        super.tearDown();
    }

    public void assertDenyGrantAllow(String identity, Operation operation, ObjectType objectType)
    {
        assertDenyGrantAllow(identity, operation, objectType, ObjectProperties.EMPTY);
    }
    
    public void assertDenyGrantAllow(String identity, Operation operation, ObjectType objectType, ObjectProperties properties)
    {
        assertEquals(Result.DENIED, _ruleSet.check(identity, operation, objectType, properties));
        _ruleSet.grant(0, identity, Permission.ALLOW, operation, objectType, properties);
        assertEquals(1, _ruleSet.getRuleCount());
        assertEquals(Result.ALLOWED, _ruleSet.check(identity, operation, objectType, properties));
    }

    public void testEmptyRuleSet()
    {
        assertNotNull(_ruleSet);
        assertEquals(_ruleSet.getRuleCount(), 0);
        assertEquals(_ruleSet.getDefault(), _ruleSet.check("user", Operation.ACCESS, ObjectType.VIRTUALHOST, ObjectProperties.EMPTY));
    }
    
    public void testVirtualHostAccess() throws Exception
    {
        assertDenyGrantAllow("user", Operation.ACCESS, ObjectType.VIRTUALHOST);
    }

    public void testQueueCreateNamed() throws Exception
    {
        assertDenyGrantAllow("user", Operation.CREATE, ObjectType.QUEUE, new ObjectProperties(_queueName));
    }

    public void testQueueCreatenamedNullRoutingKey()
    {
        ObjectProperties properties = new ObjectProperties(_queueName);
        properties.put(ObjectProperties.Property.ROUTING_KEY, (String) null);
        
        assertDenyGrantAllow("user", Operation.CREATE, ObjectType.QUEUE, properties);
    }

    public void testExchangeCreate()
    {
        ObjectProperties properties = new ObjectProperties(_exchangeName);
        properties.put(ObjectProperties.Property.TYPE, _exchangeType.asString());
        
        assertDenyGrantAllow("user", Operation.CREATE, ObjectType.EXCHANGE, properties);
    }

    public void testConsume()
    {
        assertDenyGrantAllow("user", Operation.CONSUME, ObjectType.QUEUE);
    }

    public void testPublish()
    {
        assertDenyGrantAllow("user", Operation.PUBLISH, ObjectType.EXCHANGE);
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
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, temporary));
        _ruleSet.grant(0, "user", Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, temporary);
        assertEquals(1, _ruleSet.getRuleCount());
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, temporary));
        
        // defer to global if exists, otherwise default answer - this is handled by the security manager
        assertEquals(Result.DEFER, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, normal));
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
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, temporary));

        // should not matter if the temporary permission is processed first or last
        _ruleSet.grant(1, "user", Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, normal);
        _ruleSet.grant(2, "user", Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, temporary);
        assertEquals(2, _ruleSet.getRuleCount());
        
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, normal));
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, temporary));
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
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, temporary));

        // should not matter if the temporary permission is processed first or last
        _ruleSet.grant(1, "user", Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, temporary);
        _ruleSet.grant(2, "user", Permission.ALLOW, Operation.CONSUME, ObjectType.QUEUE, normal);
        assertEquals(2, _ruleSet.getRuleCount());
        
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, normal));
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CONSUME, ObjectType.QUEUE, temporary));
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
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));

        _ruleSet.grant(1, "user", Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, named);
        _ruleSet.grant(2, "user", Permission.DENY, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        assertEquals(2, _ruleSet.getRuleCount());
        
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));
    }
    
    /**
     * The more specific rule is first, so those requests are denied.
     */
    public void testFirstTemporarySecondNamedQueueDenied()
    {
        ObjectProperties named = new ObjectProperties(_queueName);
        ObjectProperties namedTemporary = new ObjectProperties(_queueName);
        namedTemporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));

        _ruleSet.grant(1, "user", Permission.DENY, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        _ruleSet.grant(2, "user", Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, named);
        assertEquals(2, _ruleSet.getRuleCount());
        
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));
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
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedDurable));

        _ruleSet.grant(1, "user", Permission.DENY, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        _ruleSet.grant(2, "user", Permission.DENY, Operation.CREATE, ObjectType.QUEUE, namedDurable);
        _ruleSet.grant(3, "user", Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, named);
        assertEquals(3, _ruleSet.getRuleCount());
        
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedDurable));
    }
    
    public void testNamedTemporaryQueueAllowed()
    {
        ObjectProperties named = new ObjectProperties(_queueName);
        ObjectProperties namedTemporary = new ObjectProperties(_queueName);
        namedTemporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));

        _ruleSet.grant(1, "user", Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        _ruleSet.grant(2, "user", Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, named);
        assertEquals(2, _ruleSet.getRuleCount());
        
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));
    }
    
    public void testNamedTemporaryQueueDeniedAllowed()
    {
        ObjectProperties named = new ObjectProperties(_queueName);
        ObjectProperties namedTemporary = new ObjectProperties(_queueName);
        namedTemporary.put(ObjectProperties.Property.AUTO_DELETE, Boolean.TRUE);
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));

        _ruleSet.grant(1, "user", Permission.ALLOW, Operation.CREATE, ObjectType.QUEUE, namedTemporary);
        _ruleSet.grant(2, "user", Permission.DENY, Operation.CREATE, ObjectType.QUEUE, named);
        assertEquals(2, _ruleSet.getRuleCount());
        
        assertEquals(Result.DENIED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, named));
        assertEquals(Result.ALLOWED, _ruleSet.check("user", Operation.CREATE, ObjectType.QUEUE, namedTemporary));
    }
}
