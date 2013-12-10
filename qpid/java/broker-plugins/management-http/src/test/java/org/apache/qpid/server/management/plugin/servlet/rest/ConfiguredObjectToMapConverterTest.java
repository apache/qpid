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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.apache.qpid.server.management.plugin.servlet.rest.ConfiguredObjectToMapConverter.STATISTICS_MAP_KEY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Statistics;

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

        Statistics mockStatistics = createMockStatistics(statisticName, statisticValue);
        when(_configuredObject.getStatistics()).thenReturn(mockStatistics);

        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject, ConfiguredObject.class, 0);
        Map<String, Object> statsAsMap = (Map<String, Object>) resultMap.get(STATISTICS_MAP_KEY);
        assertNotNull("Statistics should be part of map", statsAsMap);
        assertEquals("Unexpected number of statistics", 1, statsAsMap.size());
        assertEquals("Unexpected statistic value", statisticValue, statsAsMap.get(statisticName));
    }

    public void testConfiguredObjectWithSingleNonConfiguredObjectAttribute() throws Exception
    {
        final String attributeName = "attribute";
        final String attributeValue = "value";
        configureMockToReturnOneAttribute(_configuredObject, attributeName, attributeValue);

        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject, ConfiguredObject.class, 0);
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

        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject, ConfiguredObject.class, 0);
        assertEquals("Unexpected number of attributes", 1, resultMap.size());
        assertEquals("Unexpected attribute value", "attributeConfiguredObjectName", resultMap.get(attributeName));
    }

    public void testConfiguredObjectWithChildAndDepth1()
    {
        final String childAttributeName = "childattribute";
        final String childAttributeValue = "childvalue";

        Model model = createTestModel();
        _converter.setModel(model);

        TestChild mockChild = mock(TestChild.class);
        configureMockToReturnOneAttribute(mockChild, childAttributeName, childAttributeValue);
        when(_configuredObject.getChildren(TestChild.class)).thenReturn(Arrays.asList(mockChild));

        Map<String, Object> resultMap = _converter.convertObjectToMap(_configuredObject, ConfiguredObject.class, 1);
        assertEquals("Unexpected parent map size", 1, resultMap.size());

        final List<Map<String, Object>> childList = (List<Map<String, Object>>) resultMap.get("testchilds");
        assertEquals("Unexpected number of children", 1, childList.size());
        final Map<String, Object> childMap = childList.get(0);
        assertEquals("Unexpected child map size", 1, childMap.size());
        assertNotNull(childMap);

        assertEquals("Unexpected child attribute value", childAttributeValue, childMap.get(childAttributeName));
    }

    private Model createTestModel()
    {
        Model model = mock(Model.class);
        final List<Class<? extends ConfiguredObject>> list = new ArrayList<Class<? extends ConfiguredObject>>();
        list.add(TestChild.class);
        when(model.getChildTypes(ConfiguredObject.class)).thenReturn(list);
        return model;
    }

    private void configureMockToReturnOneAttribute(ConfiguredObject mockConfiguredObject, String attributeName, Object attributeValue)
    {
        when(mockConfiguredObject.getAttributeNames()).thenReturn(Arrays.asList(attributeName));
        when(mockConfiguredObject.getAttribute(attributeName)).thenReturn(attributeValue);
    }

    private Statistics createMockStatistics(String statName, int statValue)
    {
        Statistics mockStatistics = mock(Statistics.class);
        when(mockStatistics.getStatisticNames()).thenReturn(Arrays.asList(statName));
        when(mockStatistics.getStatistic(statName)).thenReturn(statValue);
        return mockStatistics;
    }

    private static interface TestChild extends ConfiguredObject
    {
    }
}
