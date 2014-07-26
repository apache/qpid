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

import org.apache.commons.cli.CommandLine;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * Test to verify the command line parsing within the Main class, by
 * providing it a series of command line arguments and verifying the
 * BrokerOptions emerging for use in starting the Broker instance.
 */
public class MainTest extends QpidTestCase
{
    private Exception _startupException;

    public void testNoOptionsSpecified()
    {
        String qpidWork = "/qpid/work";
        setTestSystemProperty(BrokerProperties.PROPERTY_QPID_WORK, qpidWork);
        String qpidHome = "/qpid/home";
        setTestSystemProperty(BrokerProperties.PROPERTY_QPID_HOME, qpidHome);

        String expectedStorePath = new File(qpidWork, BrokerOptions.DEFAULT_CONFIG_NAME_PREFIX + ".json").getAbsolutePath();
        String expectedLogConfigPath = new File(qpidHome, BrokerOptions.DEFAULT_LOG_CONFIG_FILE).getAbsolutePath();

        BrokerOptions options = startDummyMain("");

        assertEquals("JSON", options.getConfigurationStoreType());
        assertEquals(expectedStorePath, options.getConfigurationStoreLocation());
        assertEquals(expectedLogConfigPath, options.getLogConfigFileLocation());
        assertEquals(0, options.getLogWatchFrequency());
        assertEquals(BrokerOptions.DEFAULT_INITIAL_CONFIG_LOCATION, options.getInitialConfigurationLocation());
        assertFalse(options.isOverwriteConfigurationStore());
        assertFalse(options.isManagementMode());
        assertEquals(0, options.getManagementModeJmxPortOverride());
        assertEquals(0, options.getManagementModeRmiPortOverride());
        assertEquals(0, options.getManagementModeHttpPortOverride());
    }

    public void testConfigurationStoreLocation()
    {
        BrokerOptions options = startDummyMain("-sp abcd/config.xml");
        assertEquals("abcd/config.xml", options.getConfigurationStoreLocation());

        options = startDummyMain("-store-path abcd/config2.xml");
        assertEquals("abcd/config2.xml", options.getConfigurationStoreLocation());
    }

    public void testConfigurationStoreType()
    {
        BrokerOptions options = startDummyMain("-st dby");
        assertEquals("dby", options.getConfigurationStoreType());

        options = startDummyMain("-store-type bdb");
        assertEquals("bdb", options.getConfigurationStoreType());
    }

    public void testOverwriteConfigurationStore()
    {
        BrokerOptions options = startDummyMain("-os");
        assertTrue(options.isOverwriteConfigurationStore());

        options = startDummyMain("-overwrite-store");
        assertTrue(options.isOverwriteConfigurationStore());
    }

    public void testLogConfig()
    {
        BrokerOptions options = startDummyMain("-l wxyz/log4j.xml");

        assertEquals("wxyz/log4j.xml", options.getLogConfigFileLocation());
    }

    public void testLogWatch()
    {
        BrokerOptions options = startDummyMain("-w 9");

        assertEquals(9, options.getLogWatchFrequency());
    }

    public void testVersion()
    {
        final TestMain main = new TestMain("-v".split("\\s"));

        assertNotNull("Command line not parsed correctly", main.getCommandLine());
        assertTrue("Parsed command line didnt pick up version option", main.getCommandLine().hasOption("v"));
    }

    public void testHelp()
    {
        final TestMain main = new TestMain("-h".split("\\s"));

        assertNotNull("Command line not parsed correctly", main.getCommandLine());
        assertTrue("Parsed command line didnt pick up help option", main.getCommandLine().hasOption("h"));
    }

    public void testInitailConfigurationLocation()
    {
        BrokerOptions options = startDummyMain("-icp abcd/initial-config.json");
        assertEquals("abcd/initial-config.json", options.getInitialConfigurationLocation());

        options = startDummyMain("-initial-config-path abcd/initial-config.json");
        assertEquals("abcd/initial-config.json", options.getInitialConfigurationLocation());
    }

    public void testManagementMode()
    {
        BrokerOptions options = startDummyMain("-mm");
        assertTrue(options.isManagementMode());

        options = startDummyMain("--management-mode");
        assertTrue(options.isManagementMode());
    }

    public void testManagementModeRmiPortOverride()
    {
        BrokerOptions options = startDummyMain("-mm -mmrmi 7777");
        assertTrue(options.isManagementMode());
        assertEquals(7777, options.getManagementModeRmiPortOverride());

        options = startDummyMain("-mm --management-mode-rmi-registry-port 7777");
        assertTrue(options.isManagementMode());
        assertEquals(7777, options.getManagementModeRmiPortOverride());

        options = startDummyMain("-mmrmi 7777");
        assertEquals(0, options.getManagementModeRmiPortOverride());
    }

    public void testManagementModeJmxPortOverride()
    {
        BrokerOptions options = startDummyMain("-mm -mmjmx 8888");
        assertTrue(options.isManagementMode());
        assertEquals(8888, options.getManagementModeJmxPortOverride());

        options = startDummyMain("-mm --management-mode-jmx-connector-port 8888");
        assertTrue(options.isManagementMode());
        assertEquals(8888, options.getManagementModeJmxPortOverride());

        options = startDummyMain("-mmjmx 8888");
        assertEquals(0, options.getManagementModeJmxPortOverride());
    }

    public void testManagementModeHttpPortOverride()
    {
        BrokerOptions options = startDummyMain("-mm -mmhttp 9999");
        assertTrue(options.isManagementMode());
        assertEquals(9999, options.getManagementModeHttpPortOverride());

        options = startDummyMain("-mm --management-mode-http-port 9999");
        assertTrue(options.isManagementMode());
        assertEquals(9999, options.getManagementModeHttpPortOverride());

        options = startDummyMain("-mmhttp 9999");
        assertEquals(0, options.getManagementModeHttpPortOverride());
    }

    public void testManagementModePassword()
    {
        String password = getTestName();
        BrokerOptions options = startDummyMain("-mm -mmpass " + password);
        assertTrue(options.isManagementMode());
        assertEquals(password, options.getManagementModePassword());

        options = startDummyMain("-mm --management-mode-password " + password);
        assertTrue(options.isManagementMode());
        assertEquals(password, options.getManagementModePassword());

        options = startDummyMain("-mmpass " + password);
        assertNotNull(options.getManagementModePassword());
    }

    public void testDefaultManagementModePassword()
    {
        BrokerOptions options = startDummyMain("-mm");
        assertTrue(options.isManagementMode());
        assertNotNull(options.getManagementModePassword());
    }

    public void testSetConfigProperties()
    {
        //short name
        String newPort = "12345";
        BrokerOptions options = startDummyMain("-prop name=value -prop " + org.apache.qpid.server.model.Broker.QPID_AMQP_PORT + "=" + newPort);

        Map<String, String> props = options.getConfigProperties();

        assertEquals(newPort, props.get(org.apache.qpid.server.model.Broker.QPID_AMQP_PORT));
        assertEquals("value", props.get("name"));

        //long name
        newPort = "678910";
        options = startDummyMain("--config-property name2=value2 --config-property " + org.apache.qpid.server.model.Broker.QPID_AMQP_PORT + "=" + newPort);

        props = options.getConfigProperties();

        assertEquals(newPort, props.get(org.apache.qpid.server.model.Broker.QPID_AMQP_PORT));
        assertEquals("value2", props.get("name2"));
    }

    public void testSetConfigPropertiesInvalidFormat()
    {
        //missing equals
        startDummyMain("-prop namevalue");
        assertTrue("expected exception did not occur",
                _startupException instanceof IllegalArgumentException);

        //no name specified
        startDummyMain("-prop =value");
        assertTrue("expected exception did not occur",
                _startupException instanceof IllegalArgumentException);
    }

    private BrokerOptions startDummyMain(String commandLine)
    {
        return (new TestMain(commandLine.split("\\s"))).getOptions();
    }

    private class TestMain extends Main
    {
        private BrokerOptions _options;

        public TestMain(String[] args)
        {
            super(args);
        }

        @Override
        protected void execute()
        {
            try
            {
                super.execute();
            }
            catch(Exception re)
            {
                MainTest.this._startupException = re;
            }
        }

        @Override
        protected void startBroker(BrokerOptions options)
        {
            _options = options;
        }

        @Override
        protected void setExceptionHandler()
        {
        }

        public BrokerOptions getOptions()
        {
            return _options;
        }

        public CommandLine getCommandLine()
        {
            return _commandLine;
        }
    }
}
