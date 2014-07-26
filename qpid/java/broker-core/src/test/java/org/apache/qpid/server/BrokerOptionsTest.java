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
package org.apache.qpid.server;

import java.io.File;
import java.util.Map;

import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.test.utils.QpidTestCase;

public class BrokerOptionsTest extends QpidTestCase
{
    private BrokerOptions _options;

    protected void setUp() throws Exception
    {
        super.setUp();
        _options = new BrokerOptions();
    }

    public void testDefaultConfigurationStoreType()
    {
        assertEquals("JSON", _options.getConfigurationStoreType());
    }

    public void testOverriddenConfigurationStoreType()
    {
        _options.setConfigurationStoreType("dby");
        assertEquals("dby", _options.getConfigurationStoreType());
    }

    public void testDefaultConfigurationStoreLocationWithQpidWork()
    {
        String qpidWork = "/test/value";
        setTestSystemProperty("QPID_WORK", qpidWork);

        String expectedPath = new File(qpidWork, BrokerOptions.DEFAULT_CONFIG_NAME_PREFIX + "." + BrokerOptions.DEFAULT_STORE_TYPE.toLowerCase()).getAbsolutePath();
        assertEquals (expectedPath, _options.getConfigurationStoreLocation());
    }

    public void testDefaultConfigurationStoreLocationWithoutQpidWork()
    {
        setTestSystemProperty("QPID_WORK", null);
        String userDir = System.getProperty("user.dir");

        String expectedPath = new File(userDir, "work/" + BrokerOptions.DEFAULT_CONFIG_NAME_PREFIX + "." + BrokerOptions.DEFAULT_STORE_TYPE.toLowerCase()).getAbsolutePath();
        assertEquals (expectedPath, _options.getConfigurationStoreLocation());
    }

    public void testDefaultConfigurationStoreLocationWithQpidWorkAndDifferentStoreType()
    {
        String qpidWork = "/test/value";
        setTestSystemProperty("QPID_WORK", qpidWork);

        String storeType = "dby";
        _options.setConfigurationStoreType(storeType);

        String expectedPath = new File(qpidWork, BrokerOptions.DEFAULT_CONFIG_NAME_PREFIX + "." + storeType).getAbsolutePath();
        assertEquals (expectedPath, _options.getConfigurationStoreLocation());
    }

    public void testOverriddenConfigurationStoreLocation()
    {
        final String testConfigFile = "/my/test/store-location.dby";
        _options.setConfigurationStoreLocation(testConfigFile);
        assertEquals(testConfigFile, _options.getConfigurationStoreLocation());
    }

    public void testDefaultLogConfigFileWithQpidHome()
    {
        String qpidHome = "/test/value";
        setTestSystemProperty(BrokerProperties.PROPERTY_QPID_HOME, qpidHome);

        String expectedPath = new File(qpidHome, BrokerOptions.DEFAULT_LOG_CONFIG_FILE).getAbsolutePath();

        assertEquals(expectedPath, _options.getLogConfigFileLocation());
    }

    public void testDefaultLogConfigFileWithoutQpidHome()
    {
        setTestSystemProperty(BrokerProperties.PROPERTY_QPID_HOME, null);

        String expectedPath = new File(BrokerOptions.DEFAULT_LOG_CONFIG_FILE).getAbsolutePath();

        assertEquals(expectedPath, _options.getLogConfigFileLocation());
    }

    public void testOverriddenLogConfigFile()
    {
        final String testLogConfigFile = "etc/mytestlog4j.xml";
        _options.setLogConfigFileLocation(testLogConfigFile);
        assertEquals(testLogConfigFile, _options.getLogConfigFileLocation());
    }

    public void testDefaultLogWatchFrequency()
    {
        assertEquals(0L, _options.getLogWatchFrequency());
    }

    public void testOverridenLogWatchFrequency()
    {
        final int myFreq = 10 * 1000;
        
        _options.setLogWatchFrequency(myFreq);
        assertEquals(myFreq, _options.getLogWatchFrequency());
    }

    public void testDefaultInitialConfigurationLocation()
    {
        assertEquals(BrokerOptions.DEFAULT_INITIAL_CONFIG_LOCATION, _options.getInitialConfigurationLocation());
    }

    public void testOverriddenInitialConfigurationLocation()
    {
        final String testConfigFile = "etc/mytestconfig.json";
        _options.setInitialConfigurationLocation(testConfigFile);
        assertEquals(testConfigFile, _options.getInitialConfigurationLocation());
    }

    public void testDefaultManagementMode()
    {
        assertEquals(false, _options.isManagementMode());
    }

    public void testOverriddenDefaultManagementMode()
    {
        _options.setManagementMode(true);
        assertEquals(true, _options.isManagementMode());
    }

    public void testDefaultManagementModeQuiesceVirtualHosts()
    {
        assertEquals(false, _options.isManagementModeQuiesceVirtualHosts());
    }

    public void testOverriddenDefaultManagementModeQuiesceVirtualHosts()
    {
        _options.setManagementModeQuiesceVirtualHosts(true);
        assertEquals(true, _options.isManagementModeQuiesceVirtualHosts());
    }

    public void testDefaultManagementModeRmiPortOverride()
    {
        assertEquals(0, _options.getManagementModeRmiPortOverride());
    }

    public void testOverriddenManagementModeRmiPort()
    {
        _options.setManagementModeRmiPortOverride(5555);
        assertEquals(5555, _options.getManagementModeRmiPortOverride());
    }

    public void testDefaultManagementModeJmxPortOverride()
    {
        assertEquals(0, _options.getManagementModeJmxPortOverride());
    }

    public void testOverriddenManagementModeJmxPort()
    {
        _options.setManagementModeJmxPortOverride(5555);
        assertEquals(5555, _options.getManagementModeJmxPortOverride());
    }

    public void testDefaultManagementModeHttpPortOverride()
    {
        assertEquals(0, _options.getManagementModeHttpPortOverride());
    }

    public void testOverriddenManagementModeHttpPort()
    {
        _options.setManagementModeHttpPortOverride(5555);
        assertEquals(5555, _options.getManagementModeHttpPortOverride());
    }

    public void testDefaultSkipLoggingConfiguration()
    {
        assertFalse(_options.isSkipLoggingConfiguration());
    }

    public void testOverriddenSkipLoggingConfiguration()
    {
        _options.setSkipLoggingConfiguration(true);
        assertTrue(_options.isSkipLoggingConfiguration());
    }

    public void testDefaultOverwriteConfigurationStore()
    {
        assertFalse(_options.isOverwriteConfigurationStore());
    }

    public void testOverriddenOverwriteConfigurationStore()
    {
        _options.setOverwriteConfigurationStore(true);
        assertTrue(_options.isOverwriteConfigurationStore());
    }

    public void testManagementModePassword()
    {
        _options.setManagementModePassword("test");
        assertEquals("Unexpected management mode password", "test", _options.getManagementModePassword());
    }

    public void testGetDefaultConfigProperties()
    {
        //Unset QPID_WORK and QPID_HOME for this test.
        //See below for specific tests of behaviour depending on their value
        setTestSystemProperty("QPID_WORK", null);
        setTestSystemProperty("QPID_HOME", null);

        Map<String,String> props = _options.getConfigProperties();

        assertEquals("unexpected number of entries", 1, props.keySet().size());

        assertTrue(props.containsKey(BrokerOptions.QPID_WORK_DIR));
        assertFalse(props.containsKey(BrokerOptions.QPID_HOME_DIR));
    }

    public void testDefaultWorkDirWithQpidWork()
    {
        String qpidWork = new File(File.separator + "test" + File.separator + "value").getAbsolutePath();
        setTestSystemProperty("QPID_WORK", qpidWork);

        assertEquals (qpidWork, _options.getConfigProperties().get(BrokerOptions.QPID_WORK_DIR));
    }

    public void testDefaultWorkDirWithoutQpidWork()
    {
        setTestSystemProperty("QPID_WORK", null);
        String userDir = System.getProperty("user.dir");

        String expectedPath = new File(userDir, "work").getAbsolutePath();
        assertEquals (expectedPath, _options.getConfigProperties().get(BrokerOptions.QPID_WORK_DIR));
    }

    public void testOverriddenWorkDir()
    {
        final String testWorkDir = "/my/test/work/dir";
        _options.setConfigProperty(BrokerOptions.QPID_WORK_DIR, testWorkDir);
        assertEquals(testWorkDir, _options.getConfigProperties().get(BrokerOptions.QPID_WORK_DIR));
    }

    public void testDefaultHomeDirWithQpidHome()
    {
        String qpidHome = new File(File.separator + "test" + File.separator + "value").getAbsolutePath();
        setTestSystemProperty("QPID_HOME", qpidHome);

        assertEquals (qpidHome, _options.getConfigProperties().get(BrokerOptions.QPID_HOME_DIR));
        assertEquals("unexpected number of entries", 2, _options.getConfigProperties().keySet().size());
    }

    public void testDefaultHomeDirWithoutQpidHome()
    {
        setTestSystemProperty("QPID_HOME", null);

        assertNull(_options.getConfigProperties().get(BrokerOptions.QPID_HOME_DIR));
        assertFalse(_options.getConfigProperties().containsKey(BrokerOptions.QPID_HOME_DIR));
        assertEquals("unexpected number of entries", 1, _options.getConfigProperties().keySet().size());
    }

    public void testOverriddenHomeDir()
    {
        final String testHomeDir = "/my/test/home/dir";
        _options.setConfigProperty(BrokerOptions.QPID_HOME_DIR, testHomeDir);
        assertEquals(testHomeDir, _options.getConfigProperties().get(BrokerOptions.QPID_HOME_DIR));
        assertEquals("unexpected number of entries", 2, _options.getConfigProperties().keySet().size());
    }

    public void testSetDefaultConfigProperties()
    {
        //Unset QPID_WORK and QPID_HOME for this test.
        //See above for specific tests of behaviour depending on their value
        setTestSystemProperty("QPID_WORK", null);
        setTestSystemProperty("QPID_HOME", null);

        String newPort = "12345";

        //set a new value for a previously defaulted port number property
        _options.setConfigProperty(org.apache.qpid.server.model.Broker.QPID_AMQP_PORT, newPort);
        Map<String,String> props = _options.getConfigProperties();
        assertEquals("unexpected number of entries", 2, props.keySet().size());
        assertEquals(newPort, props.get(org.apache.qpid.server.model.Broker.QPID_AMQP_PORT));

        //clear the value to ensure the default returns
        _options.setConfigProperty(org.apache.qpid.server.model.Broker.QPID_AMQP_PORT, null);
        props = _options.getConfigProperties();
        assertEquals("unexpected number of entries", 1, props.keySet().size());

        //set a user specified property
        _options.setConfigProperty("name", "value");
        props = _options.getConfigProperties();
        assertEquals("unexpected number of entries", 2, props.keySet().size());
        assertEquals("value", props.get("name"));
    }
}
