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

package org.apache.qpid.server.model.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;

public class AbstractConfiguredObjectImplTest extends TestCase
{

    private ConfiguredObject _concreteObject;
    private UUID _uuid = UUID.randomUUID();
    private ConfigurationChangeListener _configurationStateChangeListener = mock(ConfigurationChangeListener.class);

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _concreteObject = createParentConfiguredObject();
    }

    public void testInitialState()
    {
        assertEquals(State.INITIALISING, _concreteObject.getDesiredState());
    }

    public void testStateNotifications()
    {
        _concreteObject.addChangeListener(_configurationStateChangeListener);

        _concreteObject.setDesiredState(State.INITIALISING, State.ACTIVE);

        verify(_configurationStateChangeListener, times(1)).stateChanged(_concreteObject, State.INITIALISING, State.ACTIVE);

        _concreteObject.setDesiredState(State.ACTIVE, State.ACTIVE);

        verify(_configurationStateChangeListener, times(0)).stateChanged(_concreteObject, State.ACTIVE, State.ACTIVE);

        verifyNoMoreInteractions(_configurationStateChangeListener);
    }

    public void testSetGetAttribute()
    {
        assertNull(_concreteObject.getAttribute("test-attribute"));

        assertEquals(Integer.valueOf(1), _concreteObject.setAttribute("test-attribute", null, Integer.valueOf(1)));
        assertEquals(Integer.valueOf(1), _concreteObject.getAttribute("test-attribute"));

        assertNull(_concreteObject.setAttribute("test-attribute", Integer.valueOf(1), null));
        assertNull(_concreteObject.getAttribute("test-attribute"));
    }

    public void testSetAttributeWhenCurrentNotMatched()
    {
        assertEquals(Integer.valueOf(1), _concreteObject.setAttribute("test-attribute", null, Integer.valueOf(1)));
        assertEquals(Integer.valueOf(1), _concreteObject.getAttribute("test-attribute"));

        assertEquals(Integer.valueOf(1), _concreteObject.setAttribute("test-attribute", Integer.valueOf(2), Integer.valueOf(3)));
        assertEquals("Expected no change", Integer.valueOf(1), _concreteObject.getAttribute("test-attribute"));
    }

    public void testParentage()
    {
        TestParentCO parentCO = createParentConfiguredObject();
        assertNull("Parent should have no parent", parentCO.getParent(TestParentCO.class));

        Map<Class<? extends ConfiguredObject>, ConfiguredObject> parent = new HashMap<Class<? extends ConfiguredObject>, ConfiguredObject>();
        parent.put(TestParentCO.class, parentCO);
        TestChildCO childCO = createChildConfiguredObject(parent);

        assertEquals("Child should have its parent", parentCO, childCO.getParent(TestParentCO.class));

        TestParentCO stranger = createParentConfiguredObject();
        assertNotSame("Child should not have stranger as its parent", stranger, childCO.getParent(TestParentCO.class));

    }

    private TestParentCO createParentConfiguredObject()
    {
        return new TestParentCO(_uuid, "parent1", State.INITIALISING, true, LifetimePolicy.PERMANENT, 0l, AbstractConfiguredObject.EMPTY_ATTRIBUTE_MAP);
    }

    private TestChildCO createChildConfiguredObject(Map<Class<? extends ConfiguredObject>, ConfiguredObject> parents)
    {
        return new TestChildCO(_uuid, "parent1", State.INITIALISING, true, LifetimePolicy.PERMANENT, 0l, AbstractConfiguredObject.EMPTY_ATTRIBUTE_MAP, parents);
    }

    private final class TestParentCO extends AbstractConfiguredObject
    {
        private TestParentCO(UUID id, String name, State state, boolean durable, LifetimePolicy lifetimePolicy,
                long timeToLive, Map<String, Object> attributes)
        {
            super(id, name, state, durable, lifetimePolicy, timeToLive, attributes, EMPTY_PARENT_MAP);
        }

        @Override
        public State getActualState()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Object getLock()
        {
            return this;
        }

        @Override
        public Statistics getStatistics()
        {
            return null;
        }

        @Override
        public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
        {
            return null;
        }
    }

    private final class TestChildCO extends AbstractConfiguredObject
    {
        private TestChildCO(UUID id, String name, State state, boolean durable, LifetimePolicy lifetimePolicy,
                long timeToLive, Map<String, Object> attributes,
                Map<Class<? extends ConfiguredObject>, ConfiguredObject> parents)
        {
            super(id, name, state, durable, lifetimePolicy, timeToLive, attributes, parents);
        }

        @Override
        public State getActualState()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Object getLock()
        {
            return this;
        }

        @Override
        public Statistics getStatistics()
        {
            return null;
        }

        @Override
        public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
        {
            return null;
        }
    }

}
