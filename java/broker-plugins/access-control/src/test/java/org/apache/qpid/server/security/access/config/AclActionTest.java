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
 */
package org.apache.qpid.server.security.access.config;

import static org.mockito.Mockito.*;

import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.firewall.FirewallRule;

import junit.framework.TestCase;

public class AclActionTest extends TestCase
{
    public void testEqualsAndHashCode()
    {
        AclRulePredicates predicates = createAclRulePredicates();
        ObjectType objectType = ObjectType.EXCHANGE;
        Operation operation = Operation.ACCESS;

        AclAction aclAction = new AclAction(operation, objectType, predicates);
        AclAction equalAclAction = new AclAction(operation, objectType, predicates);

        assertTrue(aclAction.equals(aclAction));
        assertTrue(aclAction.equals(equalAclAction));
        assertTrue(equalAclAction.equals(aclAction));

        assertTrue(aclAction.hashCode() == equalAclAction.hashCode());

        assertFalse("Different operation should cause aclActions to be unequal",
                aclAction.equals(new AclAction(Operation.BIND, objectType, predicates)));

        assertFalse("Different operation type should cause aclActions to be unequal",
                aclAction.equals(new AclAction(operation, ObjectType.GROUP, predicates)));

        assertFalse("Different predicates should cause aclActions to be unequal",
                aclAction.equals(new AclAction(operation, objectType, createAclRulePredicates())));

    }

    private AclRulePredicates createAclRulePredicates()
    {
        AclRulePredicates predicates = mock(AclRulePredicates.class);
        when(predicates.getFirewallRule()).thenReturn(mock(FirewallRule.class));
        when(predicates.getObjectProperties()).thenReturn(mock(ObjectProperties.class));
        return predicates;
    }

}
