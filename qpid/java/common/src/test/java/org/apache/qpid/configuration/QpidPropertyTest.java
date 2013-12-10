/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.configuration;

import org.apache.qpid.test.utils.QpidTestCase;

public class QpidPropertyTest extends QpidTestCase
{
    private static final String TEST_VALUE1 = "TEST_VALUE1";
    private static final String TEST_VALUE2 = "TEST_VALUE2";
    private static final String DEFAULT_VALUE = "DEFAULT_VALUE";

    private String _systemPropertyName;
    private String _deprecatedSystemPropertyName;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _systemPropertyName = getName() + ".current";
        _deprecatedSystemPropertyName = getName() + ".deprecated";
    }

    public void testValueReadFromSystemProperty() throws Exception
    {
        setTestSystemProperty(_systemPropertyName, TEST_VALUE1);
        assertSystemPropertiesSet(_systemPropertyName);

        String propertyValue = QpidProperty.stringProperty(DEFAULT_VALUE, _systemPropertyName).get();
        assertEquals(TEST_VALUE1, propertyValue);
    }

    public void testValueReadFromSecondChoiceSystemPropertyWhenFirstChoiceNotSet() throws Exception
    {
        setTestSystemProperty(_deprecatedSystemPropertyName, TEST_VALUE2);
        assertSystemPropertiesSet(_deprecatedSystemPropertyName);
        assertSystemPropertiesNotSet(_systemPropertyName);

        String propertyValue = QpidProperty.stringProperty("default", _systemPropertyName, _deprecatedSystemPropertyName).get();
        assertEquals(TEST_VALUE2, propertyValue);
    }

    public void testValueReadFromFirstChoiceSystemPropertyWhenBothFirstAndSecondChoiceSet() throws Exception
    {
        setTestSystemProperty(_systemPropertyName, TEST_VALUE1);
        setTestSystemProperty(_deprecatedSystemPropertyName, TEST_VALUE2);
        assertSystemPropertiesSet(_systemPropertyName, _deprecatedSystemPropertyName);

        String propertyValue = QpidProperty.stringProperty("default", _systemPropertyName, _deprecatedSystemPropertyName).get();
        assertEquals(TEST_VALUE1, propertyValue);
    }

    public void testValueIsDefaultWhenOneSystemPropertyIsNotSet() throws Exception
    {
        assertSystemPropertiesNotSet(_systemPropertyName);

        String propertyValue = QpidProperty.stringProperty(DEFAULT_VALUE, _systemPropertyName).get();
        assertEquals(DEFAULT_VALUE, propertyValue);
    }

    public void testValueIsDefaultWhenTwoSystemPropertiesAreNotSet() throws Exception
    {
        assertSystemPropertiesNotSet(_systemPropertyName, _deprecatedSystemPropertyName);

        String propertyValue = QpidProperty.stringProperty(DEFAULT_VALUE, _systemPropertyName).get();
        assertEquals(DEFAULT_VALUE, propertyValue);
    }

    public void testValueIsNullWhenNoDefaultAndNoSystemPropertiesAreSet() throws Exception
    {
        assertSystemPropertiesNotSet(_systemPropertyName, _deprecatedSystemPropertyName);

        String nullString = null;
        String propertyValue = QpidProperty.stringProperty(nullString, _systemPropertyName).get();
        assertNull(propertyValue);
    }

    public void testBooleanValueReadFromSystemProperty() throws Exception
    {
        setTestSystemProperty(_systemPropertyName, Boolean.FALSE.toString());
        assertSystemPropertiesSet(_systemPropertyName);

        boolean propertyValue = QpidProperty.booleanProperty(Boolean.TRUE, _systemPropertyName).get();
        assertFalse(propertyValue);
    }

    public void testBooleanValueIsDefaultWhenOneSystemPropertyIsNotSet() throws Exception
    {
        assertSystemPropertiesNotSet(_systemPropertyName);

        Boolean propertyValue = QpidProperty.booleanProperty(Boolean.TRUE, _systemPropertyName).get();
        assertTrue(propertyValue);
    }

    public void testIntegerValueReadFromSystemProperty() throws Exception
    {
        int expectedValue = 15;
        setTestSystemProperty(_systemPropertyName, Integer.valueOf(expectedValue).toString());
        assertSystemPropertiesSet(_systemPropertyName);

        int propertyValue = QpidProperty.intProperty(14, _systemPropertyName).get();
        assertEquals(expectedValue, propertyValue);
    }

    public void testIntegerValueIsDefaultWhenOneSystemPropertyIsNotSet() throws Exception
    {
        int expectedValue = 15;
        assertSystemPropertiesNotSet(_systemPropertyName);

        int propertyValue = QpidProperty.intProperty(expectedValue, _systemPropertyName).get();
        assertEquals(expectedValue, propertyValue);
    }

    public void testLongValueReadFromSystemProperty() throws Exception
    {
        long expectedValue = 15;
        setTestSystemProperty(_systemPropertyName, Long.valueOf(expectedValue).toString());
        assertSystemPropertiesSet(_systemPropertyName);

        long propertyValue = QpidProperty.longProperty(14l, _systemPropertyName).get();
        assertEquals(expectedValue, propertyValue);
    }

    public void testLongValueIsDefaultWhenOneSystemPropertyIsNotSet() throws Exception
    {
        long expectedValue = 15;
        assertSystemPropertiesNotSet(_systemPropertyName);

        long propertyValue = QpidProperty.longProperty(expectedValue, _systemPropertyName).get();
        assertEquals(expectedValue, propertyValue);
    }

    public void testFloatValueReadFromSystemProperty() throws Exception
    {
        float expectedValue = 1.5f;
        setTestSystemProperty(_systemPropertyName, Float.valueOf(expectedValue).toString());
        assertSystemPropertiesSet(_systemPropertyName);

        float propertyValue = QpidProperty.floatProperty(1.5f, _systemPropertyName).get();
        assertEquals(expectedValue, propertyValue, 0.1);
    }

    public void testFloatValueIsDefaultWhenOneSystemPropertyIsNotSet() throws Exception
    {
        float expectedValue = 1.5f;
        assertSystemPropertiesNotSet(_systemPropertyName);

        float propertyValue = QpidProperty.floatProperty(expectedValue, _systemPropertyName).get();
        assertEquals(expectedValue, propertyValue, 0.1);
    }

    private void assertSystemPropertiesSet(String... systemPropertyNames)
    {
        for (String systemPropertyName : systemPropertyNames)
        {
            assertTrue("System property " + systemPropertyName + " should be set",
                    System.getProperties().containsKey(systemPropertyName));
        }
    }

    private void assertSystemPropertiesNotSet(String... systemPropertyNames)
    {
        for (String systemPropertyName : systemPropertyNames)
        {
            assertFalse("System property " + systemPropertyName + " should not be set",
                    System.getProperties().containsKey(systemPropertyName));
        }
    }

}
