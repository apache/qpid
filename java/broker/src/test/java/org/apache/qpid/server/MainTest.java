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

import org.apache.commons.cli.CommandLine;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * Test to verify the command line parsing within the Main class, by
 * providing it a series of command line arguments and verifying the
 * BrokerOptions emerging for use in starting the Broker instance.
 */
public class MainTest extends QpidTestCase
{
    public void testNoOptionsSpecified()
    {
        BrokerOptions options = startDummyMain("");

        assertEquals("json", options.getConfigurationStoreType());
        assertEquals(null, options.getConfigurationStoreLocation());
        assertEquals(null, options.getLogConfigFile());
        assertEquals(0, options.getLogWatchFrequency());
    }

    public void testConfigurationStoreLocation()
    {
        BrokerOptions options = startDummyMain("-sp abcd/config.xml");

        assertEquals("abcd/config.xml", options.getConfigurationStoreLocation());
    }

    public void testConfigurationStoreType()
    {
        BrokerOptions options = startDummyMain("-st dby");

        assertEquals("dby", options.getConfigurationStoreType());
    }

    public void testLogConfig()
    {
        BrokerOptions options = startDummyMain("-l wxyz/log4j.xml");

        assertEquals("wxyz/log4j.xml", options.getLogConfigFile());
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
