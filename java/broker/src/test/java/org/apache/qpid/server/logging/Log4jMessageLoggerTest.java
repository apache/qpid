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
package org.apache.qpid.server.logging;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.qpid.server.logging.actors.BrokerActor;

/** Test that the Log4jMessageLogger defaults behave as expected */
public class Log4jMessageLoggerTest extends TestCase
{
    Level _rootLevel;
    Log4jTestAppender _appender;

    @Override
    public void setUp() throws IOException
    {
        // Setup a file for logging
        _appender = new Log4jTestAppender();

        Logger root = Logger.getRootLogger();
        root.addAppender(_appender);

        _rootLevel = Logger.getRootLogger().getLevel();
        if (_rootLevel != Level.INFO)
        {
            root.setLevel(Level.INFO);
            root.warn("Root Logger set to:" + _rootLevel + " Resetting to INFO for test.");
        }
        root.warn("Adding Test Appender:" + _appender);
    }

    @Override
    public void tearDown()
    {
        Logger root = Logger.getRootLogger();
        root.warn("Removing Test Appender:" + _appender);
        root.warn("Resetting Root Level to : " + _rootLevel);

        Logger.getRootLogger().setLevel(_rootLevel);

        Logger.getRootLogger().removeAppender(_appender);

        //Call close on our appender. This will clear the log messages
        // from Memory
        _appender.close();
    }

    /**
     * Verify that the Log4jMessageLogger successfully logs a message.
     */
    public void testLoggedMessage()
    {
        Log4jMessageLogger msgLogger = new Log4jMessageLogger();
        assertTrue("Expected message logger to be enabled", msgLogger.isEnabled());
        
        testLoggedMessage(msgLogger, true, getName());
    }

    /**
     * Verify that for the given Log4jMessageLogger, after generating a message for the given
     * log hierarchy that the outcome is as expected.
     */
    private String testLoggedMessage(Log4jMessageLogger logger, boolean logExpected, String hierarchy)
    {
        //Create Message for test
        String message = "testDefaults";

        // Log the message
        logger.rawMessage(message, hierarchy);

        if(logExpected)
        {
            verifyLogPresent(message);
        }
        else
        {
            verifyNoLog(message);
        }
        
        return message;
    }

    /**
     * Test that specifying different log hierarchies to be used works as expected.
     * <p/>
     * Test this by using one hierarchy and verifying it succeeds, then disabling it and 
     * confirming this takes effect, and finally that using another hierarchy still succeeds.
     */
    public void testMultipleHierarchyUsage()
    {
        String loggerName1 = getName() + ".TestLogger1";
        String loggerName2 = getName() + ".TestLogger2";

        // Create a message logger to test
        Log4jMessageLogger msgLogger = new Log4jMessageLogger();
        assertTrue("Expected message logger to be enabled", msgLogger.isEnabled());
        
        //verify that using this hierarchy the message gets logged ok
        String message = testLoggedMessage(msgLogger, true, loggerName1);

        //now disable that hierarchy in log4j
        Logger.getLogger(loggerName1).setLevel(Level.OFF);
        
        //clear the previous message from the test appender
        _appender.close();
        verifyNoLog(message);
        
        //verify that the hierarchy disabling took effect
        testLoggedMessage(msgLogger, false, loggerName1);
        
        //now ensure that using a new hierarchy results in the message being output
        testLoggedMessage(msgLogger, true, loggerName2);
    }

    /**
     * Test that log4j can be used to manipulate on a per-hierarchy(and thus message) basis 
     * whether a particular status message is enabled.
     * <p/>
     * Test this by using two hierarchies, setting one off and one on (info) via log4j directly, 
     * then confirming this gives the expected isMessageEnabled() result. Then reverse the log4j
     * Levels for the Logger's and ensure the results change as expected.
     */
    public void testEnablingAndDisablingMessages()
    {
        String loggerName1 = getName() + ".TestLogger1";
        String loggerName2 = getName() + ".TestLogger2";

        Logger.getLogger(loggerName1).setLevel(Level.INFO);
        Logger.getLogger(loggerName2).setLevel(Level.OFF);
        
        Log4jMessageLogger msgLogger = new Log4jMessageLogger();
        BrokerActor actor = new BrokerActor(msgLogger);
        
        assertTrue("Expected message logger to be enabled", msgLogger.isEnabled());
        
        assertTrue("Message should be enabled", msgLogger.isMessageEnabled(actor, loggerName1));
        assertFalse("Message should be disabled", msgLogger.isMessageEnabled(actor, loggerName2));
        
        Logger.getLogger(loggerName1).setLevel(Level.WARN);
        Logger.getLogger(loggerName2).setLevel(Level.INFO);
        
        assertFalse("Message should be disabled", msgLogger.isMessageEnabled(actor, loggerName1));
        assertTrue("Message should be enabled", msgLogger.isMessageEnabled(actor, loggerName2));
    }

    /**
     * Check that the Log Message reached log4j
     * @param message the message to search for
     */
    private void verifyLogPresent(String message)
    {
        List<String> results = findMessageInLog(message);

        //Validate we only got one message
        assertEquals("The result set was not as expected.", 1, results.size());

        // Validate message
        String line = results.get(0);

        assertNotNull("No Message retrieved from log file", line);
        assertTrue("Message not contained in log.:" + line,
                   line.contains(message));
    }

    /**
     * Check that the given Message is not present in the log4j records.
     * @param message the message to search for
     */
    private void verifyNoLog(String message)
    {
        List<String> results = findMessageInLog(message);

        if (results.size() > 0)
        {
            System.err.println("Unexpected Log messages");

            for (String msg : results)
            {
                System.err.println(msg);
            }
        }

        assertEquals("No message was expected.", 0, results.size());
    }

    /**
     * Get the appenders list of events and return a list of all the messages
     * that contain the given message
     *
     * @param message the search string
     * @return The list of all logged messages that contain the search string.
     */
    private List<String> findMessageInLog(String message)
    {
        List<LoggingEvent> log = _appender.getLog();

        // Search Results for requested message
        List<String> result = new LinkedList<String>();

        for (LoggingEvent event : log)
        {
            if (String.valueOf(event.getMessage()).contains(message))
            {
                result.add(String.valueOf(event.getMessage()));
            }
        }

        return result;
    }


    /**
     * Log4j Appender that simply records all the Logging Events so we can
     * verify that the above logging will make it to log4j in a unit test.
     */
    private class Log4jTestAppender extends AppenderSkeleton
    {
        List<LoggingEvent> _log = new LinkedList<LoggingEvent>();

        protected void append(LoggingEvent loggingEvent)
        {
            _log.add(loggingEvent);
        }

        public void close()
        {
            _log.clear();
        }

        /**
         * @return the list of LoggingEvents that have occured in this Appender
         */
        public List<LoggingEvent> getLog()
        {
            return _log;
        }

        public boolean requiresLayout()
        {
            return false;
        }
    }
}

