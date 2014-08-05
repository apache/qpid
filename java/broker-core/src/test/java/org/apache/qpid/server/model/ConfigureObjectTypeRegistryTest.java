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
package org.apache.qpid.server.model;

import java.util.Collection;

import junit.framework.TestCase;

import org.apache.qpid.server.model.testmodel.Test2RootCategory;
import org.apache.qpid.server.model.testmodel.Test2RootCategoryImpl;
import org.apache.qpid.server.model.testmodel.TestModel;
import org.apache.qpid.server.model.testmodel.TestRootCategory;
import org.apache.qpid.server.model.testmodel.TestRootCategoryImpl;

public class ConfigureObjectTypeRegistryTest extends TestCase
{
    private ConfiguredObjectTypeRegistry _typeRegistry;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        Model model = TestModel.getInstance();
        _typeRegistry = model.getTypeRegistry();
    }

    public void testAllTypesRegistered()
    {
        Collection<Class<? extends ConfiguredObject>> types =
                _typeRegistry.getTypeSpecialisations(TestRootCategory.class);

        assertEquals(2, types.size());
        assertTrue(types.contains(TestRootCategoryImpl.class));

        assertTrue(types.contains(Test2RootCategoryImpl.class));
    }

    public void testTypeSpecificAttributes()
    {
        Collection<ConfiguredObjectAttribute<?, ?>> special =
                _typeRegistry.getTypeSpecificAttributes(Test2RootCategoryImpl.class);
        assertEquals(1, special.size());
        ConfiguredObjectAttribute attr = special.iterator().next();
        assertEquals("derivedAttribute",attr.getName());
        assertTrue(attr.isDerived());

        special = _typeRegistry.getTypeSpecificAttributes(TestRootCategoryImpl.class);
        assertEquals(0, special.size());

    }

    public void testDefaultedValues()
    {
        checkDefaultedValue(_typeRegistry.getAttributes((Class) TestRootCategoryImpl.class),
                            TestRootCategory.DEFAULTED_VALUE_DEFAULT);

        checkDefaultedValue(_typeRegistry.getAttributes((Class) Test2RootCategoryImpl.class),
                            Test2RootCategory.DEFAULTED_VALUE_DEFAULT);
    }

    private void checkDefaultedValue(final Collection<ConfiguredObjectAttribute<?, ?>> attrs,
                                     final String defaultedValueDefault)
    {
        boolean found = false;
        for(ConfiguredObjectAttribute<?, ?> attr : attrs)
        {
            if(attr.getName().equals("defaultedValue"))
            {
                assertEquals(defaultedValueDefault, ((ConfiguredAutomatedAttribute)attr).defaultValue());
                found = true;
                break;
            }

        }
        assertTrue("Could not find attribute defaultedValue", found);
    }
}
