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

import junit.framework.TestCase;

public class ActionTest extends TestCase
{
    private ObjectProperties _properties1 = mock(ObjectProperties.class);
    private ObjectProperties _properties2 = mock(ObjectProperties.class);

    public void testMatchesReturnsTrueForMatchingActions()
    {
        when(_properties1.matches(_properties2)).thenReturn(true);

        assertMatches(
                new Action(Operation.CONSUME, ObjectType.QUEUE, _properties1),
                new Action(Operation.CONSUME, ObjectType.QUEUE, _properties2));
    }

    public void testMatchesReturnsFalseWhenOperationsDiffer()
    {
        assertDoesntMatch(
                new Action(Operation.CONSUME, ObjectType.QUEUE, _properties1),
                new Action(Operation.CREATE, ObjectType.QUEUE, _properties1));
    }

    public void testMatchesReturnsFalseWhenOperationTypesDiffer()
    {
        assertDoesntMatch(
                new Action(Operation.CREATE, ObjectType.QUEUE, _properties1),
                new Action(Operation.CREATE, ObjectType.EXCHANGE, _properties1));
    }

    public void testMatchesReturnsFalseWhenOperationPropertiesDiffer()
    {
        assertDoesntMatch(
                new Action(Operation.CREATE, ObjectType.QUEUE, _properties1),
                new Action(Operation.CREATE, ObjectType.QUEUE, _properties2));
    }

    public void testMatchesReturnsFalseWhenMyOperationPropertiesIsNull()
    {
        assertDoesntMatch(
                new Action(Operation.CREATE, ObjectType.QUEUE, (ObjectProperties)null),
                new Action(Operation.CREATE, ObjectType.QUEUE, _properties1));
    }

    public void testMatchesReturnsFalseWhenOtherOperationPropertiesIsNull()
    {
        assertDoesntMatch(
                new Action(Operation.CREATE, ObjectType.QUEUE, _properties1),
                new Action(Operation.CREATE, ObjectType.QUEUE, (ObjectProperties)null));
    }

    public void testMatchesReturnsTrueWhenBothOperationPropertiesAreNull()
    {
        assertMatches(
                new Action(Operation.CREATE, ObjectType.QUEUE, (ObjectProperties)null),
                new Action(Operation.CREATE, ObjectType.QUEUE, (ObjectProperties)null));
    }

    private void assertMatches(Action action1, Action action2)
    {
        assertTrue(action1 + " should match " + action2, action1.matches(action2));
    }

    private void assertDoesntMatch(Action action1, Action action2)
    {
        assertFalse(action1 + " should not match " + action2, action1.matches(action2));
    }

}
