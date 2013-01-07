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
package org.apache.qpid.server.configuration;

import java.io.File;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.configuration.store.XMLConfigurationEntryStore;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public class ConfigurationEntryStoreFactoryTest extends QpidTestCase
{
    private File _userStoreLocation;
    private ConfigurationEntryStoreFactory _factory;
    private BrokerOptions _options;

    public void setUp() throws Exception
    {
        super.setUp();

        // check whether QPID_HOME JVM system property is set
        if (QPID_HOME == null)
        {
            // set the properties in order to resolve the defaults store settings
            setTestSystemProperty("QPID_HOME", TMP_FOLDER);
        }
        _factory = new ConfigurationEntryStoreFactory();
        _userStoreLocation = new File(TMP_FOLDER, "_store_" + System.currentTimeMillis() + "_" + getTestName());
        _options = new BrokerOptions();
    }

    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_userStoreLocation != null)
            {
                FileUtils.delete(_userStoreLocation, true);
            }
        }
    }

    public void testCreateJsonStoreWithDefaults()
    {
        ConfigurationEntryStore store = _factory.createStore(_userStoreLocation.getAbsolutePath(), "json", _options);
        assertNotNull("Store was not created", store);
        assertTrue("File should exists", _userStoreLocation.exists());
        assertTrue("File size should be greater than 0", _userStoreLocation.length() > 0);
        JsonConfigurationEntryStore jsonStore = new JsonConfigurationEntryStore(_userStoreLocation);
        assertFalse("Unexpected children", jsonStore.getRootEntry().getChildrenIds().isEmpty());
    }

    public void testCreateJsonStoreWithNoDefaults()
    {
        _options.setNoDefaultConfiguration(true);
        ConfigurationEntryStore store = _factory.createStore(_userStoreLocation.getAbsolutePath(), "json", _options);
        assertNotNull("Store was not created", store);
        assertTrue("File should exists", _userStoreLocation.exists());
        assertTrue("File size should be greater than 0", _userStoreLocation.length() > 0);
        JsonConfigurationEntryStore jsonStore = new JsonConfigurationEntryStore(_userStoreLocation);
        assertTrue("Unexpected children", jsonStore.getRootEntry().getChildrenIds().isEmpty());
    }

    public void testCreateDerbyStoreWithNoDefaults()
    {
        //TODO: Implement DERBY store
        _options.setNoDefaultConfiguration(true);
        try
        {
            _factory.createStore(_userStoreLocation.getAbsolutePath(), "derby", _options);
            fail("Store is not yet supported");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testCreateXmlStoreWithNoDefaults() throws Exception
    {
        //TODO: Remove XML store
        _options.setNoDefaultConfiguration(true);
        XMLConfiguration config = new XMLConfiguration();
        config.save(_userStoreLocation);
        ConfigurationEntryStore store = _factory.createStore(_userStoreLocation.getAbsolutePath(), "xml", _options);
        assertNotNull("Store was not created", store);
        assertTrue("Unexpected store type", store instanceof XMLConfigurationEntryStore);
    }

}
