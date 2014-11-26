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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.qpid.server.model.testmodel.TestManagedClass0;
import org.apache.qpid.server.model.testmodel.TestManagedClass1;
import org.apache.qpid.server.model.testmodel.TestManagedClass2;
import org.apache.qpid.server.model.testmodel.TestManagedClass3;
import org.apache.qpid.server.model.testmodel.TestManagedClass4;
import org.apache.qpid.server.model.testmodel.TestManagedClass5;
import org.apache.qpid.server.model.testmodel.TestManagedInterface1;
import org.apache.qpid.server.model.testmodel.TestManagedInterface2;
import org.apache.qpid.server.model.testmodel.Test2RootCategory;
import org.apache.qpid.server.model.testmodel.Test2RootCategoryImpl;
import org.apache.qpid.server.model.testmodel.TestChildCategory;
import org.apache.qpid.server.model.testmodel.TestModel;
import org.apache.qpid.server.model.testmodel.TestRootCategory;
import org.apache.qpid.server.model.testmodel.TestRootCategoryImpl;
import org.apache.qpid.server.plugin.ConfiguredObjectRegistration;

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

    public void testValidValues()
    {
        checkValidValues("validValue",_typeRegistry.getAttributes((Class) TestRootCategoryImpl.class),
                         Arrays.asList( TestRootCategory.VALID_VALUE1, TestRootCategory.VALID_VALUE2 ) );

        checkValidValues("validValue", _typeRegistry.getAttributes((Class) Test2RootCategoryImpl.class),
                            Test2RootCategoryImpl.functionGeneratedValidValues());


        checkValidValues("validValueNotInterpolated", _typeRegistry.getAttributes((Class) TestChildCategory.class),
                         Arrays.asList(TestChildCategory.NON_INTERPOLATED_VALID_VALUE));


    }

    public void testGetManagedInterfacesForTypeNotImplementingManagedInterfaceAndNotHavingManagedAnnotation()
    {
        ConfiguredObjectTypeRegistry typeRegistry = createConfiguredObjectTypeRegistry(TestRootCategoryImpl.class);
        assertEquals("Unexpected interfaces from object not implementing Managed interfaces",
                Collections.emptySet(), typeRegistry.getManagedInterfaces(TestRootCategory.class));
    }

    public void testGetManagedInterfacesForTypeImplementingManagedInterfaceButNotHavingManagedAnnotation()
    {
        ConfiguredObjectTypeRegistry typeRegistry = createConfiguredObjectTypeRegistry(TestRootCategoryImpl.class, TestManagedClass5.class);
        assertEquals("Unexpected interfaces from object not implementing Managed interfaces",
                Collections.emptySet(), typeRegistry.getManagedInterfaces(TestManagedClass5.class));
    }

    public void testGetManagedInterfacesForTypesImplementingManagedInterfacesWithManagedAnnotation()
    {
        ConfiguredObjectTypeRegistry typeRegistry = createConfiguredObjectTypeRegistry(TestRootCategoryImpl.class, TestManagedClass0.class, TestManagedClass1.class, TestManagedClass4.class);
        Set<Class<?>> expected = Collections.<Class<?>>singleton(TestManagedInterface1.class);
        assertEquals("Unexpected interfaces on child class", expected, typeRegistry.getManagedInterfaces(TestManagedClass1.class));
        assertEquals("Unexpected interfaces on super class", expected, typeRegistry.getManagedInterfaces(TestManagedClass0.class));
        assertEquals("Unexpected interfaces on class implementing  interface with annotation twice",
                expected, typeRegistry.getManagedInterfaces(TestManagedClass4.class));
    }

    public void testGetManagedInterfacesForTypeHavingDirectManagedAnnotation()
    {
        ConfiguredObjectTypeRegistry typeRegistry = createConfiguredObjectTypeRegistry(TestRootCategoryImpl.class, TestManagedClass2.class, TestManagedClass3.class);

        assertEquals("Unexpected interfaces on class implementing 2 interfaces with annotation",
                new HashSet<>(Arrays.asList(TestManagedInterface2.class, TestManagedInterface1.class)), typeRegistry.getManagedInterfaces(TestManagedClass2.class));
        assertEquals("Unexpected interfaces on class implementing 2 direct interfaces with annotation",
                new HashSet<>(Arrays.asList(TestManagedInterface2.class, TestManagedInterface1.class)), typeRegistry.getManagedInterfaces(TestManagedClass3.class));

    }

    private ConfiguredObjectTypeRegistry createConfiguredObjectTypeRegistry(Class<? extends ConfiguredObject>... supportedTypes)
    {
        ConfiguredObjectRegistration configuredObjectRegistration = createConfiguredObjectRegistration(supportedTypes);

        return new ConfiguredObjectTypeRegistry(Arrays.asList(configuredObjectRegistration), Arrays.asList(TestRootCategory.class, TestChildCategory.class));
    }

    private ConfiguredObjectRegistration createConfiguredObjectRegistration(final Class<? extends ConfiguredObject>... supportedTypes)
    {
        return new ConfiguredObjectRegistration()
            {
                @Override
                public Collection<Class<? extends ConfiguredObject>> getConfiguredObjectClasses()
                {
                    return Arrays.asList(supportedTypes);
                }

                @Override
                public String getType()
                {
                    return "test";
                }
            };
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

    private void checkValidValues(final String attrName, final Collection<ConfiguredObjectAttribute<?, ?>> attrs,
                                     final Collection<String> validValues)
    {
        boolean found = false;
        for(ConfiguredObjectAttribute<?, ?> attr : attrs)
        {
            if(attr.getName().equals(attrName))
            {
                Collection<String> foundValues = ((ConfiguredAutomatedAttribute<?, ?>) attr).validValues();
                assertEquals("Valid values not as expected, counts differ", validValues.size(), foundValues.size());
                assertTrue("Valid values do not include all expected values", foundValues.containsAll(validValues));
                assertTrue("Valid values contain unexpected addtional values", validValues.containsAll(foundValues));
                found = true;
                break;
            }

        }
        assertTrue("Could not find attribute " + attrName, found);
    }
}
