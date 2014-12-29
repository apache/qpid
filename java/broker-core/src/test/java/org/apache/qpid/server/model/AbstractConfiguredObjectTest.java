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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.testmodel.TestChildCategory;
import org.apache.qpid.server.model.testmodel.TestChildCategoryImpl;
import org.apache.qpid.server.model.testmodel.TestConfiguredObject;
import org.apache.qpid.server.model.testmodel.TestEnum;
import org.apache.qpid.server.model.testmodel.TestModel;
import org.apache.qpid.server.model.testmodel.TestRootCategory;
import org.apache.qpid.server.model.testmodel.TestRootCategoryImpl;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.test.utils.QpidTestCase;

public class AbstractConfiguredObjectTest extends QpidTestCase
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

        TestRootCategory object = _model.getObjectFactory().create(TestRootCategory.class,
                                                                   attributes);

        assertEquals(objectName, object.getName());
        assertEquals("override", object.getDefaultedValue());

    }

    public void testOverriddenDefaultedAttributeValueRevertedToDefault()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);
        attributes.put(TestRootCategory.DEFAULTED_VALUE, "override");

        TestRootCategory object = _model.getObjectFactory().create(TestRootCategory.class,
                                                                   attributes);

        assertEquals(objectName, object.getName());
        assertEquals("override", object.getDefaultedValue());

        object.setAttributes(Collections.singletonMap(TestRootCategory.DEFAULTED_VALUE, null));
        assertEquals(TestRootCategory.DEFAULTED_VALUE_DEFAULT, object.getDefaultedValue());
    }

    public void testEnumAttributeValueFromString()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);
        attributes.put(TestRootCategory.ENUM_VALUE, TestEnum.TEST_ENUM1.name());

        TestRootCategory object1 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                    attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(TestEnum.TEST_ENUM1, object1.getEnumValue());
    }

    public void testEnumAttributeValueFromEnum()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);
        attributes.put(TestRootCategory.ENUM_VALUE, TestEnum.TEST_ENUM1);

        TestRootCategory object1 = _model.getObjectFactory().create(TestRootCategory.class,
                                                                    attributes);

        assertEquals(objectName, object1.getName());
        assertEquals(TestEnum.TEST_ENUM1, object1.getEnumValue());
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


    public void testDefaultContextIsInContextKeys()
    {
        final String objectName = "myName";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ConfiguredObject.NAME, objectName);


        TestRootCategory object = _model.getObjectFactory().create(TestRootCategory.class,
                                                                    attributes);


        assertTrue("context default not in contextKeys",
                   object.getContextKeys(true).contains(TestRootCategory.TEST_CONTEXT_DEFAULT));
        assertEquals(object.getContextValue(String.class, TestRootCategory.TEST_CONTEXT_DEFAULT), "default");

        setTestSystemProperty(TestRootCategory.TEST_CONTEXT_DEFAULT, "notdefault");
        assertTrue("context default not in contextKeys",
                   object.getContextKeys(true).contains(TestRootCategory.TEST_CONTEXT_DEFAULT));
        assertEquals(object.getContextValue(String.class, TestRootCategory.TEST_CONTEXT_DEFAULT), "notdefault");
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
        attributes.put(ConfiguredObject.TYPE, TestChildCategoryImpl.TEST_CHILD_TYPE);

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
        attributes.put(ConfiguredObject.TYPE, TestChildCategoryImpl.TEST_CHILD_TYPE);

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

    public void testOpeningResultsInErroredStateWhenResolutionFails() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnPostResolve(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.setThrowExceptionOnPostResolve(false);
        object.setAttributes(Collections.<String, Object>singletonMap(Port.DESIRED_STATE, State.ACTIVE));
        assertTrue("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ACTIVE, object.getState());
    }

    public void testOpeningInERROREDStateAfterFailedOpenOnDesiredStateChangeToActive() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.setThrowExceptionOnOpen(false);
        object.setAttributes(Collections.<String, Object>singletonMap(Port.DESIRED_STATE, State.ACTIVE));
        assertTrue("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ACTIVE, object.getState());
    }

    public void testOpeningInERROREDStateAfterFailedOpenOnStart() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.setThrowExceptionOnOpen(false);
        object.start();
        assertTrue("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ACTIVE, object.getState());
    }

    public void testDeletionERROREDStateAfterFailedOpenOnDelete() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.delete();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.DELETED, object.getState());
    }

    public void testDeletionInERROREDStateAfterFailedOpenOnDesiredStateChangeToDelete() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        object.open();
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ERRORED, object.getState());

        object.setAttributes(Collections.<String, Object>singletonMap(Port.DESIRED_STATE, State.DELETED));
        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.DELETED, object.getState());
    }


    public void testCreationWithExceptionThrownFromValidationOnCreate() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnValidationOnCreate(true);
        try
        {
            object.create();
            fail("IllegalConfigurationException is expected to be thrown");
        }
        catch(IllegalConfigurationException e)
        {
            //pass
        }
        assertFalse("Unexpected opened", object.isOpened());
    }

    public void testCreationWithoutExceptionThrownFromValidationOnCreate() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnValidationOnCreate(false);
        object.create();
        assertTrue("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.ACTIVE, object.getState());
    }

    public void testCreationWithExceptionThrownFromOnOpen() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnOpen(true);
        try
        {
            object.create();
            fail("Exception should have been re-thrown");
        }
        catch (RuntimeException re)
        {
            // pass
        }

        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.DELETED, object.getState());
    }

    public void testCreationWithExceptionThrownFromOnCreate() throws Exception
    {
        TestConfiguredObject object = new TestConfiguredObject(getName());
        object.setThrowExceptionOnCreate(true);
        try
        {
            object.create();
            fail("Exception should have been re-thrown");
        }
        catch (RuntimeException re)
        {
            // pass
        }

        assertFalse("Unexpected opened", object.isOpened());
        assertEquals("Unexpected state", State.DELETED, object.getState());
    }

    public void testUnresolvedChildInERROREDStateIsNotValidatedOrOpenedOrAttainedDesiredStateOnParentOpen() throws Exception
    {
        TestConfiguredObject parent = new TestConfiguredObject("parent");
        TestConfiguredObject child1 = new TestConfiguredObject("child1", parent, parent.getTaskExecutor());
        child1.registerWithParents();
        TestConfiguredObject child2 = new TestConfiguredObject("child2", parent, parent.getTaskExecutor());
        child2.registerWithParents();

        child1.setThrowExceptionOnPostResolve(true);

        parent.open();

        assertTrue("Parent should be resolved", parent.isResolved());
        assertTrue("Parent should be validated", parent.isValidated());
        assertTrue("Parent should be opened", parent.isOpened());
        assertEquals("Unexpected parent state", State.ACTIVE, parent.getState());

        assertTrue("Child2 should be resolved", child2.isResolved());
        assertTrue("Child2 should be validated", child2.isValidated());
        assertTrue("Child2 should be opened", child2.isOpened());
        assertEquals("Unexpected child2 state", State.ACTIVE, child2.getState());

        assertFalse("Child2 should not be resolved", child1.isResolved());
        assertFalse("Child1 should not be validated", child1.isValidated());
        assertFalse("Child1 should not be opened", child1.isOpened());
        assertEquals("Unexpected child1 state", State.ERRORED, child1.getState());
    }

    public void testUnvalidatedChildInERROREDStateIsNotOpenedOrAttainedDesiredStateOnParentOpen() throws Exception
    {
        TestConfiguredObject parent = new TestConfiguredObject("parent");
        TestConfiguredObject child1 = new TestConfiguredObject("child1", parent, parent.getTaskExecutor());
        child1.registerWithParents();
        TestConfiguredObject child2 = new TestConfiguredObject("child2", parent, parent.getTaskExecutor());
        child2.registerWithParents();

        child1.setThrowExceptionOnValidate(true);

        parent.open();

        assertTrue("Parent should be resolved", parent.isResolved());
        assertTrue("Parent should be validated", parent.isValidated());
        assertTrue("Parent should be opened", parent.isOpened());
        assertEquals("Unexpected parent state", State.ACTIVE, parent.getState());

        assertTrue("Child2 should be resolved", child2.isResolved());
        assertTrue("Child2 should be validated", child2.isValidated());
        assertTrue("Child2 should be opened", child2.isOpened());
        assertEquals("Unexpected child2 state", State.ACTIVE, child2.getState());

        assertTrue("Child1 should be resolved", child1.isResolved());
        assertFalse("Child1 should not be validated", child1.isValidated());
        assertFalse("Child1 should not be opened", child1.isOpened());
        assertEquals("Unexpected child1 state", State.ERRORED, child1.getState());
    }

    public void testCreateEnforcesAttributeValidValues() throws Exception
    {
        final String objectName = getName();
        Map<String, Object> illegalCreateAttributes = new HashMap<>();
        illegalCreateAttributes.put(ConfiguredObject.NAME, objectName);
        illegalCreateAttributes.put(TestRootCategory.VALID_VALUE, "illegal");

        try
        {
            _model.getObjectFactory().create(TestRootCategory.class, illegalCreateAttributes);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            // PASS
        }

        Map<String, Object> legalCreateAttributes = new HashMap<>();
        legalCreateAttributes.put(ConfiguredObject.NAME, objectName);
        legalCreateAttributes.put(TestRootCategory.VALID_VALUE, TestRootCategory.VALID_VALUE1);

        TestRootCategory object = _model.getObjectFactory().create(TestRootCategory.class, legalCreateAttributes);
        assertEquals(TestRootCategory.VALID_VALUE1, object.getValidValue());
    }

    public void testChangeEnforcesAttributeValidValues() throws Exception
    {
        final String objectName = getName();
        Map<String, Object> legalCreateAttributes = new HashMap<>();
        legalCreateAttributes.put(ConfiguredObject.NAME, objectName);
        legalCreateAttributes.put(TestRootCategory.VALID_VALUE, TestRootCategory.VALID_VALUE1);

        TestRootCategory object = _model.getObjectFactory().create(TestRootCategory.class, legalCreateAttributes);
        assertEquals(TestRootCategory.VALID_VALUE1, object.getValidValue());

        object.setAttributes(Collections.singletonMap(TestRootCategory.VALID_VALUE,TestRootCategory.VALID_VALUE2));
        assertEquals(TestRootCategory.VALID_VALUE2, object.getValidValue());

        try
        {
            object.setAttributes(Collections.singletonMap(TestRootCategory.VALID_VALUE, "illegal"));
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException iae)
        {
            // PASS
        }

        assertEquals(TestRootCategory.VALID_VALUE2, object.getValidValue());

        object.setAttributes(Collections.singletonMap(TestRootCategory.VALID_VALUE,null));
        assertNull(object.getValidValue());

    }

    public void testCreateEnforcesAttributeValidValuesWithSets() throws Exception
    {
        final String objectName = getName();
        final Map<String, Object> name = Collections.singletonMap(ConfiguredObject.NAME, (Object)objectName);

        Map<String, Object> illegalCreateAttributes = new HashMap<>(name);
        illegalCreateAttributes.put(TestRootCategory.ENUMSET_VALUES, Collections.singleton(TestEnum.TEST_ENUM3));

        try
        {
            _model.getObjectFactory().create(TestRootCategory.class, illegalCreateAttributes);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            // PASS
        }

        {
            Map<String, Object> legalCreateAttributesEnums = new HashMap<>(name);
            legalCreateAttributesEnums.put(TestRootCategory.ENUMSET_VALUES,
                                           Arrays.asList(TestEnum.TEST_ENUM2, TestEnum.TEST_ENUM3));

            TestRootCategory obj = _model.getObjectFactory().create(TestRootCategory.class, legalCreateAttributesEnums);
            assertTrue(obj.getEnumSetValues().containsAll(Arrays.asList(TestEnum.TEST_ENUM2, TestEnum.TEST_ENUM3)));
        }

        {
            Map<String, Object> legalCreateAttributesStrings = new HashMap<>(name);
            legalCreateAttributesStrings.put(TestRootCategory.ENUMSET_VALUES,
                                             Arrays.asList(TestEnum.TEST_ENUM2.name(), TestEnum.TEST_ENUM3.name()));

            TestRootCategory obj = _model.getObjectFactory().create(TestRootCategory.class, legalCreateAttributesStrings);
            assertTrue(obj.getEnumSetValues().containsAll(Arrays.asList(TestEnum.TEST_ENUM2, TestEnum.TEST_ENUM3)));
        }
    }
}
