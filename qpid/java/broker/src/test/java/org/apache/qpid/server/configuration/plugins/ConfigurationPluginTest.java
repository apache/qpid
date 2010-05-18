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
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

/**
 * Test that verifies that given a configuration the
 * Plugin manager
 */
public class ConfigurationPluginTest extends TestCase
{

    class ConfigPlugin extends ConfigurationPlugin
    {
        @Override
        public String[] getElementsProcessed()
        {
                return new String[]{"[@property]", "name"};
        }

        public String getName()
        {
            return _configuration.getString("name");
        }

        public String getProperty()
        {
            return _configuration.getString("[@property]");
        }


    }

    Configuration _configuration;

    public void setUp()
    {
        XMLConfiguration xmlconfig = new XMLConfiguration();
        xmlconfig.addProperty("base.element[@property]","property");
        xmlconfig.addProperty("base.element.name","name");

        //Use a composite configuration as this is what our broker code uses.
        CompositeConfiguration composite  = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);

        _configuration = composite;
    }


    public void testValuesRetreived()
    {
       ConfigPlugin plugin = new ConfigPlugin();

        try
        {
            plugin.setConfiguration("base.element", _configuration.subset("base.element"));
        }
        catch (ConfigurationException e)
        {
            e.printStackTrace();
            fail(e.toString());
        }

        assertEquals("Name not correct","name",plugin.getName());
        assertEquals("Property not correct","property",plugin.getProperty());
    }




}
