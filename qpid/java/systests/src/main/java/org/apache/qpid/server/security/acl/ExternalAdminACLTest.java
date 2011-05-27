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
package org.apache.qpid.server.security.acl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.qpid.server.logging.management.LoggingManagementMBean;
import org.apache.qpid.test.utils.JMXTestUtils;

/**
 * Tests that ACLs can be applied to mangement operations that do not correspond to a specific AMQP object.
 * 
 * Theses tests use the logging component, exposed as the {@link LoggingManagementMBean}, to get and set properties.
 */
public class ExternalAdminACLTest extends AbstractACLTestCase
{
    private static final String CATEGORY_PRIORITY = "LogManMBeanTest.category.priority";
    private static final String CATEGORY_LEVEL = "LogManMBeanTest.category.level";
    private static final String LOGGER_LEVEL = "LogManMBeanTest.logger.level";
	
    private static final String NEWLINE = System.getProperty("line.separator");
		
    private JMXTestUtils _jmx;
    private File _testConfigFile;
    
    @Override
	public String getConfig()
    {
		return "config-systests-aclv2.xml";
    }
	
    @Override
	public List<String> getHostList()
    {
		return Arrays.asList("global");
    }
	
    @Override
	public void setUp() throws Exception
    {
		_testConfigFile = createTempTestLog4JConfig();
		
        _jmx = new JMXTestUtils(this, "admin", "admin");
        _jmx.setUp();
        super.setUp();
        _jmx.open();
    }
    
	@Override
    public void tearDown() throws Exception
    {
        _jmx.close();
		super.tearDown();
    }
	
    private File createTempTestLog4JConfig()
    {
        File tmpFile = null;
        try
        {
            tmpFile = File.createTempFile("LogManMBeanTestLog4jConfig", ".tmp");
            tmpFile.deleteOnExit();

            FileWriter fstream = new FileWriter(tmpFile);
            BufferedWriter writer = new BufferedWriter(fstream);

            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+NEWLINE);
            writer.write("<!DOCTYPE log4j:configuration SYSTEM \"log4j.dtd\">"+NEWLINE);

            writer.write("<log4j:configuration xmlns:log4j=\"http://jakarta.apache.org/log4j/\" debug=\"null\" " +
            		                                                                "threshold=\"null\">"+NEWLINE);

            writer.write("  <appender class=\"org.apache.log4j.ConsoleAppender\" name=\"STDOUT\">"+NEWLINE);
            writer.write("      <layout class=\"org.apache.log4j.PatternLayout\">"+NEWLINE);
            writer.write("          <param name=\"ConversionPattern\" value=\"%d %-5p [%t] %C{2} (%F:%L) - %m%n\"/>"+NEWLINE);
            writer.write("      </layout>"+NEWLINE);
            writer.write("  </appender>"+NEWLINE);

            //Example of a 'category' with a 'priority'
            writer.write("  <category additivity=\"true\" name=\"" + CATEGORY_PRIORITY +"\">"+NEWLINE);
            writer.write("      <priority value=\"info\"/>"+NEWLINE);
            writer.write("      <appender-ref ref=\"STDOUT\"/>"+NEWLINE);
            writer.write("  </category>"+NEWLINE);

            //Example of a 'category' with a 'level'
            writer.write("  <category additivity=\"true\" name=\"" + CATEGORY_LEVEL +"\">"+NEWLINE);
            writer.write("      <level value=\"warn\"/>"+NEWLINE);
            writer.write("      <appender-ref ref=\"STDOUT\"/>"+NEWLINE);
            writer.write("  </category>"+NEWLINE);

            //Example of a 'logger' with a 'level'
            writer.write("  <logger additivity=\"true\" name=\"" + LOGGER_LEVEL + "\">"+NEWLINE);
            writer.write("      <level value=\"error\"/>"+NEWLINE);
            writer.write("      <appender-ref ref=\"STDOUT\"/>"+NEWLINE);
            writer.write("  </logger>"+NEWLINE);

            //'root' logger
            writer.write("  <root>"+NEWLINE);
            writer.write("      <priority value=\"info\"/>"+NEWLINE);
            writer.write("      <appender-ref ref=\"STDOUT\"/>"+NEWLINE);
            writer.write("  </root>"+NEWLINE);

            writer.write("</log4j:configuration>"+NEWLINE);

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            fail("Unable to create temporary test log4j configuration");
        }

        return tmpFile;
    }
	
	public void testGetAllLoggerLevels() throws Exception
	{
		String[] levels = _jmx.getAvailableLoggerLevels();				
		for (int i = 0; i < levels.length; i++)
		{
			System.out.println(levels[i]);
		}
		assertEquals("Got incorrect number of log levels", 9, levels.length);
	}
	
	public void testGetAllLoggerLevelsDenied() throws Exception
	{
		try
		{
			_jmx.getAvailableLoggerLevels();
			fail("Got list of log levels");
		}
		catch (Exception e)
		{
			// Exception throws
			e.printStackTrace();
			assertEquals("Permission denied: Access getAvailableLoggerLevels", e.getMessage());			
		}
	}
		
	public void testChangeLoggerLevel() throws Exception
	{			
		String oldLevel = _jmx.getRuntimeRootLoggerLevel();
		System.out.println("old level = " + oldLevel);
		_jmx.setRuntimeRootLoggerLevel("DEBUG"); 
		String newLevel = _jmx.getRuntimeRootLoggerLevel();
		System.out.println("new level = " + newLevel);
		assertEquals("Logging level was not changed", "DEBUG", newLevel);
	}
	
	public void testChangeLoggerLevelDenied() throws Exception
	{
		try
		{
			_jmx.setRuntimeRootLoggerLevel("DEBUG"); 
			fail("Logging level was changed");
		}
		catch (Exception e)
		{
			assertEquals("Permission denied: Update setRuntimeRootLoggerLevel", e.getMessage());
		}
	}
}
