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
 */
package org.apache.qpid.systest.management.jmx;

import java.io.File;
import java.util.List;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.qpid.management.common.mbeans.LoggingManagement;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacadeTest;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.util.LogMonitor;

/**
 * System test for Logging Management.  <b>These tests rely on value set within
 * test-profiles/log4j-test.xml</b>.
 *
 * @see LoggingManagementMBeanTest
 * @see LoggingManagementFacadeTest
 *
 */
public class LoggingManagementTest extends QpidBrokerTestCase
{
    private JMXTestUtils _jmxUtils;
    private LoggingManagement _loggingManagement;
    private LogMonitor _monitor;

    public void setUp() throws Exception
    {
        getBrokerConfiguration().addJmxManagementConfiguration();

        _jmxUtils = new JMXTestUtils(this);

        // System test normally run with log for4j test config from beneath test-profiles.   We need to
        // copy it as some of our tests write to this file.

        File tmpLogFile = File.createTempFile("log4j" + "." + getName(), ".xml");
        tmpLogFile.deleteOnExit();
        FileUtils.copy(getBrokerCommandLog4JFile(), tmpLogFile);
        setBrokerCommandLog4JFile(tmpLogFile);

        super.setUp();
        _jmxUtils.open();

        _loggingManagement = _jmxUtils.getLoggingManagement();
        _monitor = new LogMonitor(_outputFile);
    }

    public void tearDown() throws Exception
    {
        try
        {
            if (_jmxUtils != null)
            {
                _jmxUtils.close();
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testViewEffectiveRuntimeLoggerLevels() throws Exception
    {
        final String qpidMainLogger = "org.apache.qpid";

        TabularData table = _loggingManagement.viewEffectiveRuntimeLoggerLevels();
        final CompositeData row = table.get(new String[] {qpidMainLogger} );
        assertChannelRow(row, qpidMainLogger, "DEBUG");
    }

    public void testViewConfigFileLoggerLevels() throws Exception
    {
        final String operationalLoggingLogger = "qpid.message";

        TabularData table = _loggingManagement.viewConfigFileLoggerLevels();
        final CompositeData row = table.get(new String[] {operationalLoggingLogger} );
        assertChannelRow(row, operationalLoggingLogger, "INFO");
    }

    public void testTurnOffOrgApacheQpidAtRuntime() throws Exception
    {
        final String logger = "org.apache.qpid";
        _monitor.markDiscardPoint();
        _loggingManagement.setRuntimeLoggerLevel(logger, "OFF");

        List<String> matches = _monitor.waitAndFindMatches("Setting level to OFF for logger 'org.apache.qpid'", 5000);
        assertEquals(1, matches.size());

        TabularData table = _loggingManagement.viewEffectiveRuntimeLoggerLevels();
        final CompositeData row1 = table.get(new String[] {logger} );
        assertChannelRow(row1, logger, "OFF");
    }

    public void testChangesToConfigFileBecomeEffectiveAfterReload() throws Exception
    {
        final String operationalLoggingLogger  = "qpid.message";
        assertEffectiveLoggingLevel(operationalLoggingLogger, "INFO");

        _monitor.markDiscardPoint();
        _loggingManagement.setConfigFileLoggerLevel(operationalLoggingLogger, "OFF");

        List<String> matches = _monitor.waitAndFindMatches("Setting level to OFF for logger 'qpid.message'", 5000);
        assertEquals(1, matches.size());

        assertEffectiveLoggingLevel(operationalLoggingLogger, "INFO");

        _loggingManagement.reloadConfigFile();

        assertEffectiveLoggingLevel(operationalLoggingLogger, "OFF");
    }

    private void assertEffectiveLoggingLevel(String operationalLoggingLogger, String expectedLevel)
    {
        TabularData table = _loggingManagement.viewEffectiveRuntimeLoggerLevels();
        final CompositeData row1 = table.get(new String[] {operationalLoggingLogger} );
        assertChannelRow(row1, operationalLoggingLogger, expectedLevel);
    }

    private void assertChannelRow(final CompositeData row, String logger, String level)
    {
        assertNotNull("No row for  " + logger, row);
        assertEquals("Unexpected logger name", logger, row.get(LoggingManagement.LOGGER_NAME));
        assertEquals("Unexpected level", level, row.get(LoggingManagement.LOGGER_LEVEL));
    }

}
