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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.server.model.testmodel.TestChildCategory;
import org.apache.qpid.server.model.testmodel.TestModel;
import org.apache.qpid.server.model.testmodel.TestRootCategory;
import org.apache.qpid.server.store.ConfiguredObjectRecord;

public class AbstractConfiguredObjectTest extends TestCase
{
    private final Model _model = TestModel.getInstance();

    public void testAttributePersistence()
    {
        final String objectName = "testNonPersistAttributes";
        TestRootCategory object =
                _model.getObjectFactory().create(TestRootCategory.class,
                                                Collections.<String, Object>singletonMap(ConfiguredObject.NAME,
                                                                                         objectName)
                                               );

        assertEquals(objectName, object.getName());
        assertNull(object.getAutomatedNonPersistedValue());
        assertNull(object.getAutomatedPersistedValue());

        ConfiguredObjectRecord record = object.asObjectRecord();

        assertEquals(objectName, record.getAttributes().get(ConfiguredObject.NAME));

        assertFalse(record.getAttributes().containsKey(TestRootCategory.AUTOMATED_PERSISTED_VALUE));
        assertFalse(record.getAttributes().containsKey(TestRootCategory.AUTOMATED_NONPERSISTED_VALUE));


        Map<String, Object> updatedAttributes = new HashMap<>();

        final String newValue = "newValue";

        updatedAttributes.put(TestRootCategory.AUTOMATED_PERSISTED_VALUE, newValue);
        updatedAttributes.put(TestRootCategory.AUTOMATED_NONPERSISTED_VALUE, newValue);
        object.setAttributes(updatedAttributes);

        assertEquals(newValue, object.getAutomatedPersistedValue());
        assertEquals(newValue, object.getAutomatedNonPersistedValue());

        record = object.asObjectRecord();
        assertEquals(objectName, record.getAttributes().get(ConfiguredObject.NAME));
        assertEquals(newValue, record.getAttributes().get(TestRootCategory.AUTOMATED_PERSISTED_VALUE));

        assertFalse(record.getAttributes().containsKey(TestRootCategory.AUTOMATED_NONPERSISTED_VALUE));

    }

    public void testDefaultedAttributeValue()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = Collections.<String, Object>singletonMap(ConfiguredObject.NAME, objectName);

        TestRootCategory object1 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                   attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(TestRootCategory.DEFAULTED_VALUE_DEFAULT, object1.getDefaultedValue());
    }

    public void testOverriddenDefaultedAttributeValue()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);
        attributes.put(TestRootCategory.DEFAULTED_VALUE, "override");

        TestRootCategory object2 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                   attributes);

        assertEquals(objectName, object2.getName());
        assertEquals("override", object2.getDefaultedValue());
    }

    public void testStringAttributeValueFromContextVariableProvidedBySystemProperty()
    {
        String sysPropertyName = "testStringAttributeValueFromContextVariableProvidedBySystemProperty";
        String contextToken = "${" + sysPropertyName + "}";

        System.setProperty(sysPropertyName, "myValue");

        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);
        attributes.put(TestRootCategory.STRING_VALUE, contextToken);

        TestRootCategory object1 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                    attributes);

        assertEquals(objectName, object1.getName());
        assertEquals("myValue", object1.getStringValue());

        // System property set empty string

        System.setProperty(sysPropertyName, "");
        TestRootCategory object2 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                    attributes);

        assertEquals("", object2.getStringValue());

        // System property not set
        System.clearProperty(sysPropertyName);

        TestRootCategory object3 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                    attributes);

        // yields the unexpanded token - not sure if this is really useful behaviour?
        assertEquals(contextToken, object3.getStringValue());
    }

    public void testMapAttributeValueFromContextVariableProvidedBySystemProperty()
    {
        String sysPropertyName = "testMapAttributeValueFromContextVariableProvidedBySystemProperty";
        String contextToken = "${" + sysPropertyName + "}";

        Map<String,String> expectedMap = new HashMap<>();
        expectedMap.put("field1", "value1");
        expectedMap.put("field2", "value2");

        System.setProperty(sysPropertyName, "{ \"field1\" : \"value1\", \"field2\" : \"value2\"}");

        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);
        attributes.put(TestRootCategory.MAP_VALUE, contextToken);

        TestRootCategory object1 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                    attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(expectedMap, object1.getMapValue());

        // System property not set
        System.clearProperty(sysPropertyName);
    }

    public void testStringAttributeValueFromContextVariableProvidedObjectsContext()
    {
        String contextToken = "${myReplacement}";

        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);
        attributes.put(ConfiguredObject.CONTEXT, Collections.singletonMap("myReplacement", "myValue"));
        attributes.put(TestRootCategory.STRING_VALUE, contextToken);

        TestRootCategory object1 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                    attributes);
        // Check the object's context itself
        assertTrue(object1.getContext().containsKey("myReplacement"));
        assertEquals("myValue", object1.getContext().get("myReplacement"));

        assertEquals(objectName, object1.getName());
        assertEquals("myValue", object1.getStringValue());
    }

    public void testCreationOfObjectWithInvalidInterpolatedValues()
    {
        final String parentName = "parent";
        TestRootCategory parent =
                _model.getObjectFactory().create(TestRootCategory.class,
                                                 Collections.<String, Object>singletonMap(ConfiguredObject.NAME,
                                                                                          parentName)
                                                );

        parent.setAttributes(Collections.singletonMap(ConfiguredObject.CONTEXT,
                                                      Collections.singletonMap("contextVal", "foo")));

        final Map<String, Object> attributes = new HashMap<>();
        attributes.put("intValue", "${contextVal}");
        attributes.put("name", "child");
        attributes.put("integerSet", "[ ]");
        attributes.put(ConfiguredObject.TYPE, "test");

        try
        {
            _model.getObjectFactory().create(TestChildCategory.class, attributes, parent);
            fail("creation of child object should have failed due to invalid value");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
            String message = e.getMessage();
            assertTrue("Message does not contain the attribute name", message.contains("intValue"));
            assertTrue("Message does not contain the non-interpolated value", message.contains("contextVal"));
            assertTrue("Message does not contain the interpolated value", message.contains("foo"));

        }

        assertTrue("Child should not have been registered with parent",
                   parent.getChildren(TestChildCategory.class).isEmpty());
    }

    public void testCreationOfObjectWithInvalidDefaultValues()
    {
        final String parentName = "parent";
        TestRootCategory parent =
                _model.getObjectFactory().create(TestRootCategory.class,
                                                 Collections.<String, Object>singletonMap(ConfiguredObject.NAME,
                                                                                          parentName)
                                                );

        final Map<String, Object> attributes = new HashMap<>();
        attributes.put("intValue", "1");
        attributes.put("name", "child");
        attributes.put(ConfiguredObject.TYPE, "test");

        try
        {
            _model.getObjectFactory().create(TestChildCategory.class, attributes, parent);
            fail("creation of child object should have failed due to invalid value");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
            String message = e.getMessage();
            assertTrue("Message does not contain the attribute name", message.contains("integerSet"));
            assertTrue("Message does not contain the error value", message.contains("foo"));

        }

        assertTrue("Child should not have been registered with parent",
                   parent.getChildren(TestChildCategory.class).isEmpty());
    }

}
