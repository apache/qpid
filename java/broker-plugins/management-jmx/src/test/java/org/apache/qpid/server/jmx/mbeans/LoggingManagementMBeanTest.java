/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.jmx.mbeans;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.anyString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import junit.framework.TestCase;

import org.apache.qpid.management.common.mbeans.LoggingManagement;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacade;

public class LoggingManagementMBeanTest extends TestCase
{
    private static final String TEST_LEVEL1 = "LEVEL1";
    private static final String TEST_LEVEL2 = "LEVEL2";

    private LoggingManagementMBean _loggingMBean;
    private LoggingManagementFacade _mockLoggingFacade;
    private ManagedObjectRegistry _mockManagedObjectRegistry;

    @Override
    protected void setUp() throws Exception
    {
        _mockLoggingFacade = mock(LoggingManagementFacade.class);
        final List<String> listOfLevels = new ArrayList<String>()
        {{
            add(TEST_LEVEL1);
            add(TEST_LEVEL2);
        }};
        when(_mockLoggingFacade.getAvailableLoggerLevels()).thenReturn(listOfLevels);

        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);

        _loggingMBean = new LoggingManagementMBean(_mockLoggingFacade, _mockManagedObjectRegistry);
    }

    public void testMBeanRegistersItself() throws Exception
    {
        LoggingManagementMBean connectionMBean = new LoggingManagementMBean(_mockLoggingFacade, _mockManagedObjectRegistry);
        verify(_mockManagedObjectRegistry).registerObject(connectionMBean);
    }

    public void testLog4jLogWatchInterval() throws Exception
    {
        final Integer value = 5000;
        when(_mockLoggingFacade.getLog4jLogWatchInterval()).thenReturn(value);

        assertEquals("Unexpected watch interval",value, _loggingMBean.getLog4jLogWatchInterval());
    }

    public void testGetAvailableLoggerLevels()  throws Exception
    {
        String[] actualLevels = _loggingMBean.getAvailableLoggerLevels();
        assertEquals(3, actualLevels.length);
        assertEquals(TEST_LEVEL1, actualLevels[0]);
        assertEquals(TEST_LEVEL2, actualLevels[1]);
        assertEquals(LoggingManagementMBean.INHERITED_PSUEDO_LOG_LEVEL, actualLevels[2]);
    }

    public void testViewEffectiveRuntimeLoggerLevels()  throws Exception
    {
        Map<String, String> loggerLevels = new TreeMap<String, String>();
        loggerLevels.put("a.b.D", TEST_LEVEL2);
        loggerLevels.put("a.b.C", TEST_LEVEL1);
        loggerLevels.put("a.b.c.E", TEST_LEVEL2);

        when(_mockLoggingFacade.retrieveRuntimeLoggersLevels()).thenReturn(loggerLevels );

        TabularData table = _loggingMBean.viewEffectiveRuntimeLoggerLevels();
        assertEquals(3, table.size());

        final CompositeData row1 = table.get(new String[] {"a.b.C"} );
        final CompositeData row2 = table.get(new String[] {"a.b.D"} );
        final CompositeData row3 = table.get(new String[] {"a.b.c.E"} );
        assertChannelRow(row1, "a.b.C", TEST_LEVEL1);
        assertChannelRow(row2, "a.b.D", TEST_LEVEL2);
        assertChannelRow(row3, "a.b.c.E", TEST_LEVEL2);
    }

    public void testGetRuntimeRootLoggerLevel()  throws Exception
    {
        when(_mockLoggingFacade.retrieveRuntimeRootLoggerLevel()).thenReturn(TEST_LEVEL1);

        assertEquals(TEST_LEVEL1, _loggingMBean.getRuntimeRootLoggerLevel());
    }

    public void testSetRuntimeRootLoggerLevel()  throws Exception
    {
        _loggingMBean.setRuntimeRootLoggerLevel(TEST_LEVEL1);
        verify(_mockLoggingFacade).setRuntimeRootLoggerLevel(TEST_LEVEL1);
    }

    public void testSetRuntimeRootLoggerLevelWhenLoggingLevelUnknown()  throws Exception
    {
        boolean result = _loggingMBean.setRuntimeRootLoggerLevel("unknown");
        assertFalse(result);
        verify(_mockLoggingFacade, never()).setRuntimeRootLoggerLevel("unknown");
    }

    public void testSetRuntimeRootLoggerLevelWhenLoggingLevelInherited()  throws Exception
    {
        boolean result = _loggingMBean.setRuntimeRootLoggerLevel(LoggingManagementMBean.INHERITED_PSUEDO_LOG_LEVEL);
        assertFalse(result);
        verify(_mockLoggingFacade, never()).setRuntimeRootLoggerLevel(anyString());
    }

    public void testSetRuntimeLoggerLevel() throws Exception
    {
        _loggingMBean.setRuntimeLoggerLevel("a.b.c.D", TEST_LEVEL1);
        verify(_mockLoggingFacade).setRuntimeLoggerLevel("a.b.c.D", TEST_LEVEL1);
    }

    public void testSetRuntimeLoggerLevelWhenLoggingLevelUnknown()  throws Exception
    {
        boolean result = _loggingMBean.setRuntimeLoggerLevel("a.b.c.D", "unknown");
        assertFalse(result);
        verify(_mockLoggingFacade, never()).setRuntimeLoggerLevel(anyString(), anyString());
    }

    public void testSetRuntimeLoggerLevelWhenLoggingLevelInherited()  throws Exception
    {
        boolean result = _loggingMBean.setRuntimeLoggerLevel("a.b.c.D", LoggingManagementMBean.INHERITED_PSUEDO_LOG_LEVEL);
        assertTrue(result);
        verify(_mockLoggingFacade).setRuntimeLoggerLevel("a.b.c.D", null);
    }

    public void testViewEffectiveConfigFileLoggerLevels()  throws Exception
    {
        Map<String, String> loggerLevels = new TreeMap<String, String>();
        loggerLevels.put("a.b.D", "level2");
        loggerLevels.put("a.b.C", TEST_LEVEL1);
        loggerLevels.put("a.b.c.E", "level2");

        when(_mockLoggingFacade.retrieveConfigFileLoggersLevels()).thenReturn(loggerLevels );

        TabularData table = _loggingMBean.viewConfigFileLoggerLevels();
        assertEquals(3, table.size());

        final CompositeData row1 = table.get(new String[] {"a.b.C"} );
        final CompositeData row2 = table.get(new String[] {"a.b.D"} );
        final CompositeData row3 = table.get(new String[] {"a.b.c.E"} );
        assertChannelRow(row1, "a.b.C", TEST_LEVEL1);
        assertChannelRow(row2, "a.b.D", TEST_LEVEL2);
        assertChannelRow(row3, "a.b.c.E", TEST_LEVEL2);
    }

    public void testGetConfigFileRootLoggerLevel() throws Exception
    {
        when(_mockLoggingFacade.retrieveConfigFileRootLoggerLevel()).thenReturn(TEST_LEVEL1);

        assertEquals(TEST_LEVEL1, _loggingMBean.getConfigFileRootLoggerLevel());
    }

    public void testSetConfigFileRootLoggerLevel()  throws Exception
    {
        when(_mockLoggingFacade.getAvailableLoggerLevels()).thenReturn(Collections.singletonList(TEST_LEVEL1));
        _loggingMBean.setConfigFileRootLoggerLevel(TEST_LEVEL1);
        verify(_mockLoggingFacade).setConfigFileRootLoggerLevel(TEST_LEVEL1);
    }

    public void testSetConfigFileRootLoggerLevelWhenLoggingLevelUnknown()  throws Exception
    {
        when(_mockLoggingFacade.getAvailableLoggerLevels()).thenReturn(Collections.singletonList(TEST_LEVEL1));
        boolean result = _loggingMBean.setConfigFileRootLoggerLevel("unknown");
        assertFalse(result);
        verify(_mockLoggingFacade, never()).setConfigFileRootLoggerLevel("unknown");
    }

    public void testSetConfigFileRootLoggerLevelWhenLoggingLevelInherited()  throws Exception
    {
        when(_mockLoggingFacade.getAvailableLoggerLevels()).thenReturn(Collections.singletonList(TEST_LEVEL1));
        boolean result = _loggingMBean.setConfigFileRootLoggerLevel(LoggingManagementMBean.INHERITED_PSUEDO_LOG_LEVEL);
        assertFalse(result);
        verify(_mockLoggingFacade, never()).setConfigFileRootLoggerLevel(anyString());
    }

    public void testSetConfigFileLoggerLevel() throws Exception
    {
        when(_mockLoggingFacade.getAvailableLoggerLevels()).thenReturn(Collections.singletonList(TEST_LEVEL1));
        _loggingMBean.setConfigFileLoggerLevel("a.b.c.D", TEST_LEVEL1);
        verify(_mockLoggingFacade).setConfigFileLoggerLevel("a.b.c.D", TEST_LEVEL1);
    }

    public void testSetConfigFileLoggerLevelWhenLoggingLevelUnknown()  throws Exception
    {
        when(_mockLoggingFacade.getAvailableLoggerLevels()).thenReturn(Collections.singletonList(TEST_LEVEL1));
        boolean result = _loggingMBean.setConfigFileLoggerLevel("a.b.c.D", "unknown");
        assertFalse(result);
        verify(_mockLoggingFacade, never()).setConfigFileLoggerLevel("a.b.c.D", "unknown");
    }

    public void testSetConfigFileLoggerLevelWhenLoggingLevelInherited()  throws Exception
    {
        when(_mockLoggingFacade.getAvailableLoggerLevels()).thenReturn(Collections.singletonList(TEST_LEVEL1));
        boolean result = _loggingMBean.setConfigFileLoggerLevel("a.b.c.D", LoggingManagementMBean.INHERITED_PSUEDO_LOG_LEVEL);
        assertTrue(result);
        verify(_mockLoggingFacade).setConfigFileLoggerLevel("a.b.c.D", null);
    }

    public void testReloadConfigFile() throws Exception
    {
        _loggingMBean.reloadConfigFile();

        verify(_mockLoggingFacade).reload();
    }

    private void assertChannelRow(final CompositeData row, String logger, String level)
    {
        assertNotNull("No row for  " + logger, row);
        assertEquals("Unexpected logger name", logger, row.get(LoggingManagement.LOGGER_NAME));
        assertEquals("Unexpected level", level, row.get(LoggingManagement.LOGGER_LEVEL));
    }
}
