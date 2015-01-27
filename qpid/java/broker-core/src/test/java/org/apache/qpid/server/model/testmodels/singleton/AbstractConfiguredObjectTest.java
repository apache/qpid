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
package org.apache.qpid.server.model.testmodels.singleton;

import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * Tests behaviour of AbstractConfiguredObject related to attributes including
 * persistence, defaulting, and attribute values derived from context variables.
 */
public class AbstractConfiguredObjectTest extends QpidTestCase
{
    private final Model _model = TestModel.getInstance();

    public void testAttributePersistence()
    {
        final String objectName = "testNonPersistAttributes";
        TestSingleton object =
                _model.getObjectFactory().create(TestSingleton.class,
                                                Collections.<String, Object>singletonMap(ConfiguredObject.NAME,
                                                                                         objectName)
                                               );

        assertEquals(objectName, object.getName());
        assertNull(object.getAutomatedNonPersistedValue());
        assertNull(object.getAutomatedPersistedValue());
        assertEquals(TestSingletonImpl.DERIVED_VALUE, object.getDerivedValue());

        ConfiguredObjectRecord record = object.asObjectRecord();

        assertEquals(objectName, record.getAttributes().get(ConfiguredObject.NAME));

        assertFalse(record.getAttributes().containsKey(TestSingleton.AUTOMATED_PERSISTED_VALUE));
        assertFalse(record.getAttributes().containsKey(TestSingleton.AUTOMATED_NONPERSISTED_VALUE));
        assertFalse(record.getAttributes().containsKey(TestSingleton.DERIVED_VALUE));

        Map<String, Object> updatedAttributes = new HashMap<>();

        final String newValue = "newValue";

        updatedAttributes.put(TestSingleton.AUTOMATED_PERSISTED_VALUE, newValue);
        updatedAttributes.put(TestSingleton.AUTOMATED_NONPERSISTED_VALUE, newValue);
        updatedAttributes.put(TestSingleton.DERIVED_VALUE, System.currentTimeMillis());  // Will be ignored
        object.setAttributes(updatedAttributes);

        assertEquals(newValue, object.getAutomatedPersistedValue());
        assertEquals(newValue, object.getAutomatedNonPersistedValue());

        record = object.asObjectRecord();
        assertEquals(objectName, record.getAttributes().get(ConfiguredObject.NAME));
        assertEquals(newValue, record.getAttributes().get(TestSingleton.AUTOMATED_PERSISTED_VALUE));

        assertFalse(record.getAttributes().containsKey(TestSingleton.AUTOMATED_NONPERSISTED_VALUE));
        assertFalse(record.getAttributes().containsKey(TestSingleton.DERIVED_VALUE));

    }

    public void testDefaultedAttributeValue()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = Collections.<String, Object>singletonMap(TestSingleton.NAME, objectName);

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
                                                                   attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(TestSingleton.DEFAULTED_VALUE_DEFAULT, object1.getDefaultedValue());
    }

    public void testOverriddenDefaultedAttributeValue()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.DEFAULTED_VALUE, "override");

        TestSingleton object = _model.getObjectFactory().create(TestSingleton.class,
                                                                   attributes);

        assertEquals(objectName, object.getName());
        assertEquals("override", object.getDefaultedValue());

    }

    public void testOverriddenDefaultedAttributeValueRevertedToDefault()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.DEFAULTED_VALUE, "override");

        TestSingleton object = _model.getObjectFactory().create(TestSingleton.class,
                                                                   attributes);

        assertEquals(objectName, object.getName());
        assertEquals("override", object.getDefaultedValue());

        object.setAttributes(Collections.singletonMap(TestSingleton.DEFAULTED_VALUE, null));

        assertEquals(TestSingleton.DEFAULTED_VALUE_DEFAULT, object.getDefaultedValue());
    }

    public void testEnumAttributeValueFromString()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.ENUM_VALUE, TestEnum.TEST_ENUM1.name());

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
                                                                    attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(TestEnum.TEST_ENUM1, object1.getEnumValue());
    }

    public void testEnumAttributeValueFromEnum()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.ENUM_VALUE, TestEnum.TEST_ENUM1);

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
                                                                    attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(TestEnum.TEST_ENUM1, object1.getEnumValue());
    }

    public void testIntegerAttributeValueFromString()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.INT_VALUE, "-4");

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
                                                                     attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(-4, object1.getIntValue());
    }

    public void testIntegerAttributeValueFromInteger()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.INT_VALUE, 5);

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
                                                                     attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(5, object1.getIntValue());
    }

    public void testIntegerAttributeValueFromDouble()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.INT_VALUE, 6.1);

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
                                                                     attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(6, object1.getIntValue());
    }

    public void testStringAttributeValueFromContextVariableProvidedBySystemProperty()
    {
        String sysPropertyName = "testStringAttributeValueFromContextVariableProvidedBySystemProperty";
        String contextToken = "${" + sysPropertyName + "}";

        System.setProperty(sysPropertyName, "myValue");

        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.STRING_VALUE, contextToken);

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
                                                                    attributes);

        assertEquals(objectName, object1.getName());
        assertEquals("myValue", object1.getStringValue());

        // System property set empty string

        System.setProperty(sysPropertyName, "");
        TestSingleton object2 = _model.getObjectFactory().create(TestSingleton.class,
                                                                    attributes);

        assertEquals("", object2.getStringValue());

        // System property not set
        System.clearProperty(sysPropertyName);

        TestSingleton object3 = _model.getObjectFactory().create(TestSingleton.class,
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
        attributes.put(TestSingleton.NAME, objectName);
        attributes.put(TestSingleton.MAP_VALUE, contextToken);

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
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
        attributes.put(TestSingleton.STRING_VALUE, contextToken);

        TestSingleton object1 = _model.getObjectFactory().create(TestSingleton.class,
                                                                    attributes);
        // Check the object's context itself
        assertTrue(object1.getContext().containsKey("myReplacement"));
        assertEquals("myValue", object1.getContext().get("myReplacement"));

        assertEquals(objectName, object1.getName());
        assertEquals("myValue", object1.getStringValue());
    }

    public void testInvalidIntegerAttributeValueFromContextVariable()
    {
        final Map<String, Object> attributes = new HashMap<>();

        attributes.put(TestSingleton.NAME, "myName");
        attributes.put(TestSingleton.TYPE, TestSingletonImpl.TEST_SINGLETON_TYPE);
        attributes.put(TestSingleton.CONTEXT, Collections.singletonMap("contextVal", "notAnInteger"));
        attributes.put(TestSingleton.INT_VALUE, "${contextVal}");

        try
        {
            _model.getObjectFactory().create(TestSingleton.class, attributes);
            fail("creation of child object should have failed due to invalid value");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
            String message = e.getMessage();
            assertTrue("Message does not contain the attribute name", message.contains("intValue"));
            assertTrue("Message does not contain the non-interpolated value", message.contains("contextVal"));
            assertTrue("Message does not contain the interpolated value", message.contains("contextVal"));

        }
    }

    public void testCreateEnforcesAttributeValidValues() throws Exception
    {
        final String objectName = getName();
        Map<String, Object> illegalCreateAttributes = new HashMap<>();
        illegalCreateAttributes.put(ConfiguredObject.NAME, objectName);
        illegalCreateAttributes.put(TestSingleton.VALID_VALUE, "illegal");

        try
        {
            _model.getObjectFactory().create(TestSingleton.class, illegalCreateAttributes);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            // PASS
        }

        Map<String, Object> legalCreateAttributes = new HashMap<>();
        legalCreateAttributes.put(ConfiguredObject.NAME, objectName);
        legalCreateAttributes.put(TestSingleton.VALID_VALUE, TestSingleton.VALID_VALUE1);

        TestSingleton object = _model.getObjectFactory().create(TestSingleton.class, legalCreateAttributes);
        assertEquals(TestSingleton.VALID_VALUE1, object.getValidValue());
    }

    public void testChangeEnforcesAttributeValidValues() throws Exception
    {
        final String objectName = getName();
        Map<String, Object> legalCreateAttributes = new HashMap<>();
        legalCreateAttributes.put(ConfiguredObject.NAME, objectName);
        legalCreateAttributes.put(TestSingleton.VALID_VALUE, TestSingleton.VALID_VALUE1);

        TestSingleton object = _model.getObjectFactory().create(TestSingleton.class, legalCreateAttributes);
        assertEquals(TestSingleton.VALID_VALUE1, object.getValidValue());

        object.setAttributes(Collections.singletonMap(TestSingleton.VALID_VALUE, TestSingleton.VALID_VALUE2));
        assertEquals(TestSingleton.VALID_VALUE2, object.getValidValue());

        try
        {
            object.setAttributes(Collections.singletonMap(TestSingleton.VALID_VALUE, "illegal"));
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException iae)
        {
            // PASS
        }

        assertEquals(TestSingleton.VALID_VALUE2, object.getValidValue());

        object.setAttributes(Collections.singletonMap(TestSingleton.VALID_VALUE,null));
        assertNull(object.getValidValue());

    }

    public void testCreateEnforcesAttributeValidValuesWithSets() throws Exception
    {
        final String objectName = getName();
        final Map<String, Object> name = Collections.singletonMap(ConfiguredObject.NAME, (Object)objectName);

        Map<String, Object> illegalCreateAttributes = new HashMap<>(name);
        illegalCreateAttributes.put(TestSingleton.ENUMSET_VALUES, Collections.singleton(TestEnum.TEST_ENUM3));

        try
        {
            _model.getObjectFactory().create(TestSingleton.class, illegalCreateAttributes);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            // PASS
        }

        {
            Map<String, Object> legalCreateAttributesEnums = new HashMap<>(name);
            legalCreateAttributesEnums.put(TestSingleton.ENUMSET_VALUES,
                                           Arrays.asList(TestEnum.TEST_ENUM2, TestEnum.TEST_ENUM3));

            TestSingleton obj = _model.getObjectFactory().create(TestSingleton.class, legalCreateAttributesEnums);
            assertTrue(obj.getEnumSetValues().containsAll(Arrays.asList(TestEnum.TEST_ENUM2, TestEnum.TEST_ENUM3)));
        }

        {
            Map<String, Object> legalCreateAttributesStrings = new HashMap<>(name);
            legalCreateAttributesStrings.put(TestSingleton.ENUMSET_VALUES,
                                             Arrays.asList(TestEnum.TEST_ENUM2.name(), TestEnum.TEST_ENUM3.name()));

            TestSingleton
                    obj = _model.getObjectFactory().create(TestSingleton.class, legalCreateAttributesStrings);
            assertTrue(obj.getEnumSetValues().containsAll(Arrays.asList(TestEnum.TEST_ENUM2, TestEnum.TEST_ENUM3)));
        }
    }

    public void testDefaultContextIsInContextKeys()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);

        TestSingleton object = _model.getObjectFactory().create(TestSingleton.class,
                                                                    attributes);


        assertTrue("context default not in contextKeys",
                   object.getContextKeys(true).contains(TestSingleton.TEST_CONTEXT_DEFAULT));
        assertEquals(object.getContextValue(String.class, TestSingleton.TEST_CONTEXT_DEFAULT), "default");

        setTestSystemProperty(TestSingleton.TEST_CONTEXT_DEFAULT, "notdefault");
        assertTrue("context default not in contextKeys",
                   object.getContextKeys(true).contains(TestSingleton.TEST_CONTEXT_DEFAULT));
        assertEquals(object.getContextValue(String.class, TestSingleton.TEST_CONTEXT_DEFAULT), "notdefault");
    }

    public void testDerivedAttributeValue()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);

        TestSingleton object = _model.getObjectFactory().create(TestSingleton.class, attributes);
        assertEquals(TestSingletonImpl.DERIVED_VALUE, object.getDerivedValue());

        // Check that update is ignored
        object.setAttribute(TestSingleton.DERIVED_VALUE, object.getDerivedValue(), System.currentTimeMillis());

        assertEquals(TestSingletonImpl.DERIVED_VALUE, object.getDerivedValue());
    }

    public void testSecureValueRetrieval()
    {
        final String objectName = "myName";
        final String secret = "secret";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);
        attributes.put(TestSingleton.SECURE_VALUE, secret);

        final TestSingleton object = _model.getObjectFactory().create(TestSingleton.class, attributes);

        assertEquals(AbstractConfiguredObject.SECURED_STRING_VALUE, object.getAttribute(TestSingleton.SECURE_VALUE));
        assertEquals(secret, object.getSecureValue());

        //verify we can retrieve the actual secure value using system rights
        Subject.doAs(org.apache.qpid.server.security.SecurityManager.getSubjectWithAddedSystemRights(),
                     new PrivilegedAction<Object>()
                     {
                         @Override
                         public Object run()
                         {
                             assertEquals(secret, object.getAttribute(TestSingleton.SECURE_VALUE));
                             assertEquals(secret, object.getSecureValue());
                             return null;
                         }
                     });


    }
}
