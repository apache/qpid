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
package org.apache.qpid.server.configuration.plugins;

import junit.framework.TestCase;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

import java.util.List;

/**
 * Test that verifies that given a Configuration a ConfigurationPlugin can
 * process and validate that data.
 */
public class ConfigurationPluginTest extends InternalBrokerBaseCase
{
    private static final double DOUBLE = 3.14;
    private static final long POSITIVE_LONG = 1000;
    private static final long NEGATIVE_LONG = -1000;
    private static final int LIST_SIZE = 3;

    class ConfigPlugin extends ConfigurationPlugin
    {
        @Override
        public String[] getElementsProcessed()
        {
            return new String[]{"[@property]", "name",
                                "positiveLong", "negativeLong",
                                "true", "list", "double"};
        }

        @Override
        public void validateConfiguration() throws ConfigurationException
        {
            // no validation requried
        }

        public String getName()
        {
            return getStringValue("name");
        }

        public String getProperty()
        {
            return getStringValue("[@property]");
        }


    }

    ConfigPlugin _plugin;

    @Override
    public void setUp() throws Exception
    {
        // Test does not directly use the AppRegistry but the configured broker
        // is required for the correct ConfigurationPlugin processing
        super.setUp();
        XMLConfiguration xmlconfig = new XMLConfiguration();
        xmlconfig.addProperty("base.element[@property]", "property");
        xmlconfig.addProperty("base.element.name", "name");
        // We make these strings as that is how they will be read from the file.
        xmlconfig.addProperty("base.element.positiveLong", String.valueOf(POSITIVE_LONG));
        xmlconfig.addProperty("base.element.negativeLong", String.valueOf(NEGATIVE_LONG));
        xmlconfig.addProperty("base.element.boolean", String.valueOf(true));
        xmlconfig.addProperty("base.element.double", String.valueOf(DOUBLE));
        for (int i = 0; i < LIST_SIZE; i++)
        {
            xmlconfig.addProperty("base.element.list", i);
        }

        //Use a composite configuration as this is what our broker code uses.
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        _plugin = new ConfigPlugin();

        try
        {
            _plugin.setConfiguration("base.element", composite.subset("base.element"));
        }
        catch (ConfigurationException e)
        {
            e.printStackTrace();
            fail(e.toString());
        }

    }

    public void testHasConfiguration()
    {
        assertTrue("Plugin has no configuration ", _plugin.hasConfiguration());
        _plugin = new ConfigPlugin();
        assertFalse("Plugins has configuration", _plugin.hasConfiguration());
    }

    public void testValuesRetreived()
    {
        assertEquals("Name not correct", "name", _plugin.getName());
        assertEquals("Property not correct", "property", _plugin.getProperty());
    }

    public void testContainsPositiveLong()
    {
        assertTrue("positiveLong is not positive", _plugin.containsPositiveLong("positiveLong"));
        assertFalse("NonExistentValue was found", _plugin.containsPositiveLong("NonExistentValue"));

        try
        {
            _plugin.validatePositiveLong("positiveLong");
        }
        catch (ConfigurationException e)
        {
            fail(e.getMessage());
        }

        try
        {
            _plugin.validatePositiveLong("negativeLong");
            fail("negativeLong should not be positive");
        }
        catch (ConfigurationException e)
        {
            assertEquals("negativeLong should not be reported as positive",
                         "ConfigPlugin: unable to configure invalid negativeLong:" + NEGATIVE_LONG, e.getMessage());
        }

    }

    public void testDouble()
    {
        assertEquals("Double value not returned", DOUBLE, _plugin.getDoubleValue("double"));
        assertEquals("default Double value not returned", 0.0, _plugin.getDoubleValue("NonExistent"));
        assertEquals("set default Double value not returned", DOUBLE, _plugin.getDoubleValue("NonExistent", DOUBLE));
    }

    public void testLong()
    {
        assertTrue("Long value not returned", _plugin.containsLong("positiveLong"));
        assertFalse("Long value returned", _plugin.containsLong("NonExistent"));
        assertEquals("Long value not returned", POSITIVE_LONG, _plugin.getLongValue("positiveLong"));
        assertEquals("default Long value not returned", 0, _plugin.getLongValue("NonExistent"));
        assertEquals("set default Long value not returned", NEGATIVE_LONG, _plugin.getLongValue("NonExistent", NEGATIVE_LONG));
    }

    public void testInt()
    {
        assertTrue("Int value not returned", _plugin.containsInt("positiveLong"));
        assertFalse("Int value returned", _plugin.containsInt("NonExistent"));
        assertEquals("Int value not returned", (int) POSITIVE_LONG, _plugin.getIntValue("positiveLong"));
        assertEquals("default Int value not returned", 0, _plugin.getIntValue("NonExistent"));
        assertEquals("set default Int value not returned", (int) NEGATIVE_LONG, _plugin.getIntValue("NonExistent", (int) NEGATIVE_LONG));
    }

    public void testString()
    {
        assertEquals("String value not returned", "name", _plugin.getStringValue("name"));
        assertNull("Null default String value not returned", _plugin.getStringValue("NonExistent", null));
        assertNull("default String value not returned", _plugin.getStringValue("NonExistent"));
        assertEquals("default String value not returned", "Default", _plugin.getStringValue("NonExistent", "Default"));
    }

    public void testBoolean()
    {
        assertTrue("Boolean value not returned", _plugin.containsBoolean("boolean"));
        assertFalse("Boolean value not returned", _plugin.containsBoolean("NonExistent"));
        assertTrue("Boolean value not returned", _plugin.getBooleanValue("boolean"));
        assertFalse("default String value not returned", _plugin.getBooleanValue("NonExistent"));
        assertTrue("set default String value not returned", _plugin.getBooleanValue("NonExistent", true));
    }

    public void testList()
    {
        assertTrue("list not found in plugin", _plugin.contains("list"));
        List list = _plugin.getListValue("list");
        assertNotNull("Returned list should not be null", list);
        assertEquals("List should not be empty", LIST_SIZE, list.size());

        list = _plugin.getListValue("NonExistent");
        assertNotNull("Returned list should not be null", list);
        assertEquals("List is not empty", 0, list.size());
    }

    public void testContains()
    {
        assertTrue("list not found in plugin", _plugin.contains("list"));
        assertFalse("NonExistent found in plugin", _plugin.contains("NonExistent"));
    }

}
