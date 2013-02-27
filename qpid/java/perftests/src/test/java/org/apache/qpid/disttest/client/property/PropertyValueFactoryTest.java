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
package org.apache.qpid.disttest.client.property;

import org.apache.qpid.test.utils.QpidTestCase;

public class PropertyValueFactoryTest extends QpidTestCase
{
    private PropertyValueFactory _factory;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _factory = new PropertyValueFactory();
    }

    public void testCreateListPropertyValue()
    {
        PropertyValue propertyValue = _factory.createPropertyValue("list");
        assertNotNull("List generator is not created", propertyValue);
        assertTrue("Unexpected type of list generator", propertyValue instanceof ListPropertyValue);
    }

    public void testCreateRangePropertyValue()
    {
        PropertyValue propertyValue = _factory.createPropertyValue("range");
        assertNotNull("Range generator is not created", propertyValue);
        assertTrue("Unexpected type of range generator", propertyValue instanceof RangePropertyValue);
    }

    public void testCreateRandomPropertyValue()
    {
        PropertyValue propertyValue = _factory.createPropertyValue("random");
        assertNotNull("Random generator is not created", propertyValue);
        assertTrue("Unexpected type of range generator", propertyValue instanceof RandomPropertyValue);
    }

    public void testCreateSimplePropertyValue()
    {
        PropertyValue propertyValue = _factory.createPropertyValue("simple");
        assertNotNull("Simple property value is not created", propertyValue);
        assertTrue("Unexpected type of property value", propertyValue instanceof SimplePropertyValue);
    }

    public void testCreateNonExistingPropertyValue()
    {
        try
        {
            _factory.createPropertyValue("nonExisting");
            fail("Non existing property value should not be created");
        }
        catch (Exception e)
        {
            // pass
        }
    }
}
