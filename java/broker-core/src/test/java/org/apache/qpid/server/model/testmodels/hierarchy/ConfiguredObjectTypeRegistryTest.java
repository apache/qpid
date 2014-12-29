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
package org.apache.qpid.server.model.testmodels.hierarchy;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectTypeRegistry;
import org.apache.qpid.server.model.ManagedInterface;

public class ConfiguredObjectTypeRegistryTest extends TestCase
{
    private ConfiguredObjectTypeRegistry _typeRegistry = TestModel.getInstance().getTypeRegistry();

    public void testTypeSpecialisations()
    {
        Collection<Class<? extends ConfiguredObject>> types = _typeRegistry.getTypeSpecialisations(TestEngine.class);

        assertEquals("Unexpected number of specialisations for " + TestEngine.class + " Found : " + types, 3, types.size());
        assertTrue(types.contains(TestPetrolEngineImpl.class));
        assertTrue(types.contains(TestHybridEngineImpl.class));
        assertTrue(types.contains(TestElecEngineImpl.class));
    }

    public void testGetValidChildTypes()
    {
        // The standard car restricts its engine type
        Collection<String> standardCarValidEnginesTypes = _typeRegistry.getValidChildTypes(TestStandardCarImpl.class, TestEngine.class);
        assertThat(standardCarValidEnginesTypes, hasItem(TestPetrolEngineImpl.TEST_PETROL_ENGINE_TYPE));
        assertThat(standardCarValidEnginesTypes, hasItem(TestHybridEngineImpl.TEST_HYBRID_ENGINE_TYPE));
        assertThat(standardCarValidEnginesTypes.size(), is(2));

        Collection<String> kitCarValidEngineTypes = _typeRegistry.getValidChildTypes(TestKitCarImpl.class, TestEngine.class);
        // Would it be more useful to producers of management UIs if this were populated with all possible types?
        assertNull(kitCarValidEngineTypes);
    }

    public void testManagedInterfaces()
    {
        // The electric engine is recharable
        Set<Class<? extends ManagedInterface>> elecEngIntfcs = _typeRegistry.getManagedInterfaces(TestElecEngine.class);
        assertThat(elecEngIntfcs, hasItem(TestRechargeable.class));
        assertThat(elecEngIntfcs.size(), is(1));

        // The pertrol engine implements no additional interfaces
        Set<Class<? extends ManagedInterface>> stdCarIntfcs = _typeRegistry.getManagedInterfaces(TestPetrolEngine.class);
        assertThat(stdCarIntfcs.size(), is(0));
    }
}
