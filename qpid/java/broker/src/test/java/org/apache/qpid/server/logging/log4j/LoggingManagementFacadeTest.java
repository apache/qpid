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
 *
 */
package org.apache.qpid.server.logging.log4j;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

import junit.framework.TestCase;

public class LoggingManagementFacadeTest extends TestCase
{
    private LoggingManagementFacade _loggingFacade;
    private String _log4jXmlFile;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _log4jXmlFile = createTestLog4jXml();
        _loggingFacade = LoggingManagementFacade.configure(_log4jXmlFile);
    }

    public void testGetAvailableLoggerLevels() throws Exception
    {
        List<String> levels = _loggingFacade.getAvailableLoggerLevels();
        assertTrue(levels.contains("ALL"));
        assertTrue(levels.contains("TRACE"));
        assertTrue(levels.contains("DEBUG"));
        assertTrue(levels.contains("INFO"));
        assertTrue(levels.contains("WARN"));
        assertTrue(levels.contains("ERROR"));
        assertTrue(levels.contains("FATAL"));
        assertTrue(levels.contains("OFF"));
        assertEquals(8, levels.size());
    }

    public void testRetrieveConfigFileRootLoggerLevel() throws Exception
    {
        String level = _loggingFacade.retrieveConfigFileRootLoggerLevel();
        assertEquals(Level.WARN.toString(), level);
    }

    public void testSetConfigFileRootLoggerLevel() throws Exception
    {
        String oldLevel = _loggingFacade.retrieveConfigFileRootLoggerLevel();
        assertEquals("WARN", oldLevel);

        _loggingFacade.setConfigFileRootLoggerLevel("INFO");

        String level = _loggingFacade.retrieveConfigFileRootLoggerLevel();
        assertEquals("INFO", level);
    }

    public void testRetrieveConfigFileLoggerLevels() throws Exception
    {
        Map<String, String> levels = _loggingFacade.retrieveConfigFileLoggersLevels();
        assertEquals(3, levels.size());
        String abcLevel = levels.get("a.b.c");
        String abc1Level = levels.get("a.b.c.1");
        String abc2Level = levels.get("a.b.c.2");
        assertEquals("INFO", abcLevel);
        assertEquals("DEBUG", abc1Level);
        assertEquals("TRACE", abc2Level);
    }

    public void testSetConfigFileLoggerLevels() throws Exception
    {
        final String loggerName = "a.b.c";

        assertConfigFileLoggingLevel(loggerName, "INFO");

        _loggingFacade.setConfigFileLoggerLevel(loggerName, "WARN");

        Map<String, String> levels = _loggingFacade.retrieveConfigFileLoggersLevels();
        String abcLevel = levels.get(loggerName);
        assertEquals("WARN", abcLevel);
    }

    public void testSetConfigFileLoggerLevelsWhereLoggerDoesNotExist() throws Exception
    {
        try
        {
            _loggingFacade.setConfigFileLoggerLevel("does.not.exist", "WARN");
            fail("Exception not thrown");
        }
        catch (LoggingFacadeException lfe)
        {
            // PASS
            assertEquals("Can't find logger does.not.exist", lfe.getMessage());
        }
    }

    public void testRetrieveRuntimeRootLoggerLevel() throws Exception
    {
        String level = _loggingFacade.retrieveRuntimeRootLoggerLevel();
        assertEquals(Level.WARN.toString(), level);
    }

    public void testSetRuntimeRootLoggerLevel() throws Exception
    {
        String oldLevel = _loggingFacade.retrieveRuntimeRootLoggerLevel();
        assertEquals("WARN", oldLevel);

        _loggingFacade.setRuntimeRootLoggerLevel("INFO");

        String level = _loggingFacade.retrieveRuntimeRootLoggerLevel();
        assertEquals("INFO", level);
    }

    public void testRetrieveRuntimeLoggersLevels() throws Exception
    {
        Map<String, String> levels = _loggingFacade.retrieveRuntimeLoggersLevels();
        // Don't assert size as implementation itself uses logging and we'd count its loggers too
        String abcLevel = levels.get("a.b.c");
        String abc1Level = levels.get("a.b.c.1");
        String abc2Level = levels.get("a.b.c.2");
        assertEquals("INFO", abcLevel);
        assertEquals("DEBUG", abc1Level);
        assertEquals("TRACE", abc2Level);
    }

    public void testSetRuntimeLoggerLevel() throws Exception
    {
        final String loggerName = "a.b.c";

        assertRuntimeLoggingLevel(loggerName, "INFO");

        _loggingFacade.setRuntimeLoggerLevel(loggerName, "WARN");

        assertRuntimeLoggingLevel(loggerName, "WARN");
    }

    public void testSetRuntimeLoggerToInheritFromParent() throws Exception
    {
        final String parentLoggerName = "a.b.c";
        final String childLoggerName = "a.b.c.1";

        assertRuntimeLoggingLevel(parentLoggerName, "INFO");
        assertRuntimeLoggingLevel(childLoggerName, "DEBUG");

        _loggingFacade.setRuntimeLoggerLevel(childLoggerName, null);

        assertRuntimeLoggingLevel(parentLoggerName, "INFO");
        assertRuntimeLoggingLevel(childLoggerName, "INFO");
    }

    public void testSetRuntimeLoggerLevelsWhereLoggerDoesNotExist() throws Exception
    {
        final String loggerName = "does.not.exist2";

        Map<String, String> oldLevels = _loggingFacade.retrieveRuntimeLoggersLevels();
        assertFalse(oldLevels.containsKey(loggerName));

        try
        {
            _loggingFacade.setRuntimeLoggerLevel(loggerName, "WARN");
            fail("Exception not thrown");
        }
        catch (LoggingFacadeException lfe)
        {
            // PASS
            assertEquals("Can't find logger " + loggerName, lfe.getMessage());
        }

        Map<String, String> levels = _loggingFacade.retrieveRuntimeLoggersLevels();
        assertFalse(levels.containsKey(loggerName));
   }

    public void testReloadOfChangedLog4JFileUpdatesRuntimeLogLevel() throws Exception
    {
        final String loggerName = "a.b.c";

        assertRuntimeLoggingLevel(loggerName, "INFO");
        assertConfigFileLoggingLevel(loggerName, "INFO");

        _loggingFacade.setConfigFileLoggerLevel(loggerName, "WARN");

        assertRuntimeLoggingLevel(loggerName, "INFO");

        _loggingFacade.reload();

        assertRuntimeLoggingLevel(loggerName, "WARN");
    }


    public void testReloadOfLog4JFileRevertsRuntimeChanges() throws Exception
    {
        final String loggerName = "a.b.c";

        assertRuntimeLoggingLevel(loggerName, "INFO");
        assertConfigFileLoggingLevel(loggerName, "INFO");

        _loggingFacade.setRuntimeLoggerLevel(loggerName, "WARN");

        assertRuntimeLoggingLevel(loggerName, "WARN");

        _loggingFacade.reload();

        assertRuntimeLoggingLevel(loggerName, "INFO");
    }

    private void assertConfigFileLoggingLevel(final String loggerName, String expectedLevel) throws Exception
    {
        Map<String, String> levels = _loggingFacade.retrieveConfigFileLoggersLevels();
        String actualLevel = levels.get(loggerName);
        assertEquals(expectedLevel, actualLevel);
    }

    private void assertRuntimeLoggingLevel(final String loggerName, String expectedLevel) throws Exception
    {
        Map<String, String> levels = _loggingFacade.retrieveRuntimeLoggersLevels();
        String actualLevel = levels.get(loggerName);
        assertEquals(expectedLevel, actualLevel);
    }

    private String createTestLog4jXml() throws Exception
    {
        return TestFileUtils.createTempFileFromResource(this, "LoggingFacadeTest.log4j.xml").getAbsolutePath();
    }
}
