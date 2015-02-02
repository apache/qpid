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
 *
 */
package org.apache.qpid.server.management.plugin.servlet.rest;

import static org.apache.qpid.server.management.plugin.servlet.rest.ConfiguredObjectToMapConverter.STATISTICS_MAP_KEY;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectAttribute;
import org.apache.qpid.server.model.ConfiguredObjectTypeRegistry;
import org.apache.qpid.server.model.Model;

public class ConfiguredObjectToMapConverterTest extends TestCase
{
    private ConfiguredObjectToMapConverter _converter = new ConfiguredObjectToMapConverter();
    private ConfiguredObject _configuredObject = mock(ConfiguredObject.class);

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
    }

    public void testConfiguredObjectWithSingleStatistics() throws Exception
    {
        final String statisticName = "statisticName";
        final int statisticValue = 10;

        when(_configuredObject.getStatistics()).thenReturn(Collections.singletonMap(statisticName, (Number) statisticValue));

        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject,
                                                                      ConfiguredObject.class,
                                                                      0,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      120);
        Map<String, Object> statsAsMap = (Map<String, Object>) resultMap.get(STATISTICS_MAP_KEY);
        assertNotNull("Statistics should be part of map", statsAsMap);
        assertEquals("Unexpected number of statistics", 1, statsAsMap.size());
        assertEquals("Unexpected statistic value", statisticValue, statsAsMap.get(statisticName));
    }

    public void testConfiguredObjectWithSingleNonConfiguredObjectAttribute() throws Exception
    {
        final String attributeName = "attribute";
        final String attributeValue = "value";
        Model model = createTestModel();
        when(_configuredObject.getModel()).thenReturn(model);
        configureMockToReturnOneAttribute(_configuredObject, attributeName, attributeValue);

        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject,
                                                                      ConfiguredObject.class,
                                                                      0,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      120);
        assertEquals("Unexpected number of attributes", 1, resultMap.size());
        assertEquals("Unexpected attribute value", attributeValue, resultMap.get(attributeName));
    }

    /*
     * For now, it is the name of the configured object is returned as the attribute value, rather than the
     * configured object itself
     */
    public void testConfiguredObjectWithSingleConfiguredObjectAttribute() throws Exception
    {
        final String attributeName = "attribute";
        final ConfiguredObject attributeValue = mock(ConfiguredObject.class);
        when(attributeValue.getName()).thenReturn("attributeConfiguredObjectName");

        configureMockToReturnOneAttribute(_configuredObject, attributeName, attributeValue);

        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject,
                                                                      ConfiguredObject.class,
                                                                      0,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      120);
        assertEquals("Unexpected number of attributes", 1, resultMap.size());
        assertEquals("Unexpected attribute value", "attributeConfiguredObjectName", resultMap.get(attributeName));
    }

    public void testConfiguredObjectWithChildAndDepth1()
    {
        final String childAttributeName = "childattribute";
        final String childAttributeValue = "childvalue";

        Model model = createTestModel();

        TestChild mockChild = mock(TestChild.class);
        when(mockChild.getModel()).thenReturn(model);
        when(_configuredObject.getModel()).thenReturn(model);
        configureMockToReturnOneAttribute(mockChild, childAttributeName, childAttributeValue);
        when(_configuredObject.getChildren(TestChild.class)).thenReturn(Arrays.asList(mockChild));

        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject,
                                                                      ConfiguredObject.class,
                                                                      1,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      120);
        assertEquals("Unexpected parent map size", 1, resultMap.size());

        final List<Map<String, Object>> childList = (List<Map<String, Object>>) resultMap.get("testchilds");
        assertEquals("Unexpected number of children", 1, childList.size());
        final Map<String, Object> childMap = childList.get(0);
        assertEquals("Unexpected child map size", 1, childMap.size());
        assertNotNull(childMap);

        assertEquals("Unexpected child attribute value", childAttributeValue, childMap.get(childAttributeName));
    }

    public void testActuals()
    {
        final String childAttributeName = "childattribute";
        final String childAttributeValue = "childvalue";
        final String childActualAttributeValue = "${actualvalue}";
        final Map<String,Object> actualContext = Collections.<String,Object>singletonMap("key","value");
        final Set<String> inheritedKeys = new HashSet<>(Arrays.asList("key","inheritedkey"));

        Model model = createTestModel();

        TestChild mockChild = mock(TestChild.class);
        when(mockChild.getModel()).thenReturn(model);
        when(_configuredObject.getModel()).thenReturn(model);
        when(_configuredObject.getAttributeNames()).thenReturn(Collections.singletonList(ConfiguredObject.CONTEXT));
        when(_configuredObject.getContextValue(eq(String.class), eq("key"))).thenReturn("value");
        when(_configuredObject.getContextValue(eq(String.class),eq("inheritedkey"))).thenReturn("foo");
        when(_configuredObject.getContextKeys(anyBoolean())).thenReturn(inheritedKeys);
        when(_configuredObject.getContext()).thenReturn(actualContext);
        when(_configuredObject.getActualAttributes()).thenReturn(Collections.singletonMap(ConfiguredObject.CONTEXT, actualContext));
        when(mockChild.getAttributeNames()).thenReturn(Arrays.asList(childAttributeName, ConfiguredObject.CONTEXT));
        when(mockChild.getAttribute(childAttributeName)).thenReturn(childAttributeValue);
        when(mockChild.getActualAttributes()).thenReturn(Collections.singletonMap(childAttributeName, childActualAttributeValue));
        when(_configuredObject.getChildren(TestChild.class)).thenReturn(Arrays.asList(mockChild));


        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject,
                                                                      ConfiguredObject.class,
                                                                      1,
                                                                      true,
                                                                      false,
                                                                      false,
                                                                      false,
                                                                      120);
        assertEquals("Unexpected parent map size", 2, resultMap.size());
        assertEquals("Incorrect context", resultMap.get(ConfiguredObject.CONTEXT), actualContext);
        List<Map<String, Object>> childList = (List<Map<String, Object>>) resultMap.get("testchilds");
        assertEquals("Unexpected number of children", 1, childList.size());
        Map<String, Object> childMap = childList.get(0);
        assertNotNull(childMap);
        assertEquals("Unexpected child map size", 1, childMap.size());

        assertEquals("Unexpected child attribute value", childActualAttributeValue, childMap.get(childAttributeName));

        resultMap = _converter.convertObjectToMap(_configuredObject,
                                                  ConfiguredObject.class,
                                                  1,
                                                  false,
                                                  false,
                                                  false,
                                                  false,
                                                  120);
        assertEquals("Unexpected parent map size", 2, resultMap.size());
        Map<String, Object> inheritedContext = new HashMap<>();
        inheritedContext.put("key","value");
        inheritedContext.put("inheritedkey","foo");
        assertEquals("Incorrect context", inheritedContext, resultMap.get(ConfiguredObject.CONTEXT));
        childList = (List<Map<String, Object>>) resultMap.get("testchilds");
        assertEquals("Unexpected number of children", 1, childList.size());
        childMap = childList.get(0);
        assertEquals("Unexpected child map size", 1, childMap.size());
        assertNotNull(childMap);

        assertEquals("Unexpected child attribute value", childAttributeValue, childMap.get(childAttributeName));

    }

    public void testOversizedAttributes()
    {

        Model model = createTestModel();
        ConfiguredObjectTypeRegistry typeRegistry = model.getTypeRegistry();
        final Map<String, ConfiguredObjectAttribute<?, ?>> attributeTypes =
                typeRegistry.getAttributeTypes(TestChild.class);
        final ConfiguredObjectAttribute longAttr = mock(ConfiguredObjectAttribute.class);
        when(longAttr.isOversized()).thenReturn(true);
        when(longAttr.getOversizedAltText()).thenReturn("");
        when(attributeTypes.get(eq("longAttr"))).thenReturn(longAttr);

        TestChild mockChild = mock(TestChild.class);
        when(mockChild.getModel()).thenReturn(model);
        when(_configuredObject.getModel()).thenReturn(model);
        configureMockToReturnOneAttribute(mockChild, "longAttr", "this is not long");
        when(_configuredObject.getChildren(TestChild.class)).thenReturn(Arrays.asList(mockChild));


         Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject,
                                                                            ConfiguredObject.class,
                                                                            1,
                                                                            false,
                                                                            false,
                                                                            false,
                                                                            false,
                                                                            20);
        Object children = resultMap.get("testchilds");
        assertNotNull(children);
        assertTrue(children instanceof Collection);
        assertTrue(((Collection)children).size()==1);
        Object attrs = ((Collection)children).iterator().next();
        assertTrue(attrs instanceof Map);
        assertEquals("this is not long", ((Map) attrs).get("longAttr"));



        resultMap = _converter.convertObjectToMap(_configuredObject,
                                                  ConfiguredObject.class,
                                                  1,
                                                  false,
                                                  false,
                                                  false,
                                                  false,
                                                  8);

        children = resultMap.get("testchilds");
        assertNotNull(children);
        assertTrue(children instanceof Collection);
        assertTrue(((Collection)children).size()==1);
        attrs = ((Collection)children).iterator().next();
        assertTrue(attrs instanceof Map);
        assertEquals("this...", ((Map) attrs).get("longAttr"));




        when(longAttr.getOversizedAltText()).thenReturn("test alt text");

        resultMap = _converter.convertObjectToMap(_configuredObject,
                                                  ConfiguredObject.class,
                                                  1,
                                                  false,
                                                  false,
                                                  false,
                                                  false,
                                                  8);

        children = resultMap.get("testchilds");
        assertNotNull(children);
        assertTrue(children instanceof Collection);
        assertTrue(((Collection)children).size()==1);
        attrs = ((Collection)children).iterator().next();
        assertTrue(attrs instanceof Map);
        assertEquals("test alt text", ((Map) attrs).get("longAttr"));


    }

    private Model createTestModel()
    {
        Model model = mock(Model.class);
        final List<Class<? extends ConfiguredObject>> list = new ArrayList<Class<? extends ConfiguredObject>>();
        list.add(TestChild.class);
        when(model.getChildTypes(ConfiguredObject.class)).thenReturn(list);
        final ConfiguredObjectTypeRegistry typeRegistry = mock(ConfiguredObjectTypeRegistry.class);
        final Map<String, ConfiguredObjectAttribute<?, ?>> attrTypes = mock(Map.class);
        when(attrTypes.get(any(String.class))).thenReturn(mock(ConfiguredObjectAttribute.class));
        when(typeRegistry.getAttributeTypes(any(Class.class))).thenReturn(attrTypes);
        when(model.getTypeRegistry()).thenReturn(typeRegistry);
        return model;
    }

    private void configureMockToReturnOneAttribute(ConfiguredObject mockConfiguredObject, String attributeName, Object attributeValue)
    {
        when(mockConfiguredObject.getAttributeNames()).thenReturn(Arrays.asList(attributeName));
        when(mockConfiguredObject.getAttribute(attributeName)).thenReturn(attributeValue);
    }

    private static interface TestChild extends ConfiguredObject
    {
    }
}
