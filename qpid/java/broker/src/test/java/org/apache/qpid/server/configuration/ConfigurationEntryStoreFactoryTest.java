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
import org.apache.qpid.server.configuration.store.MergingStore;
import org.apache.qpid.server.configuration.store.XMLConfigurationEntryStore;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public class ConfigurationEntryStoreFactoryTest extends QpidTestCase
{
    private File _userStoreFile;
    private ConfigurationEntryStoreFactory _factory;
    private BrokerOptions _options;

    public void setUp() throws Exception
    {
        super.setUp();
        setTestSystemProperty("QPID_HOME", TMP_FOLDER);
        _factory = new ConfigurationEntryStoreFactory();
        _userStoreFile = new File(TMP_FOLDER, "_store_" + System.currentTimeMillis() + "_" + getTestName());
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
            if (_userStoreFile != null)
            {
                FileUtils.delete(_userStoreFile, true);
            }
        }
    }
    public void testCreateJsonStoreWithDefaults()
    {
        _options.setNoDefault(false);
        ConfigurationEntryStore store = _factory.createStore(_userStoreFile.getAbsolutePath(), "json", _options);
        assertNotNull("Store was not created", store);
        assertTrue("Unexpected store type", store instanceof MergingStore);
    }

    public void testCreateJsonStoreWithNoDefaults()
    {
        _options.setNoDefault(true);
        ConfigurationEntryStore store = _factory.createStore(_userStoreFile.getAbsolutePath(), "json", _options);
        assertNotNull("Store was not created", store);
        assertTrue("Unexpected store type", store instanceof JsonConfigurationEntryStore);
    }

    public void testCreateDerbyStoreWithNoDefaults()
    {
        _options.setNoDefault(true);
        try
        {
            _factory.createStore(_userStoreFile.getAbsolutePath(), "derby", _options);
            fail("Store is not yet supported");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testCreateBDBDerbyStoreWithNoDefaults()
    {
        _options.setNoDefault(true);
        try
        {
            _factory.createStore(_userStoreFile.getAbsolutePath(), "bdb", _options);
            fail("Store is not yet supported");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testCreateXmlStoreWithNoDefaults() throws Exception
    {
        _options.setNoDefault(true);
        XMLConfiguration config = new XMLConfiguration();
        config.save(_userStoreFile);
        ConfigurationEntryStore store = _factory.createStore(_userStoreFile.getAbsolutePath(), "xml", _options);
        assertNotNull("Store was not created", store);
        assertTrue("Unexpected store type", store instanceof XMLConfigurationEntryStore);
    }

}
