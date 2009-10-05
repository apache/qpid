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
package org.apache.qpid.server.logging.messages;

import junit.framework.TestCase;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.RootMessageLoggerImpl;
import org.apache.qpid.server.logging.actors.TestBlankActor;
import org.apache.qpid.server.logging.rawloggers.UnitTestMessageLogger;
import org.apache.qpid.server.logging.subjects.TestBlankSubject;

import java.util.List;

public abstract class AbstractTestMessages extends TestCase
{
    protected Configuration _config = new PropertiesConfiguration();
    protected LogMessage _logMessage = null;
    protected LogActor _actor;
    protected UnitTestMessageLogger _logger;
    protected LogSubject _logSubject = new TestBlankSubject();

    public void setUp() throws ConfigurationException
    {
        ServerConfiguration serverConfig = new ServerConfiguration(_config);

        _logger = new UnitTestMessageLogger();
        RootMessageLogger rootLogger =
                new RootMessageLoggerImpl(serverConfig, _logger);

        _actor = new TestBlankActor(rootLogger);
    }

    protected List<Object> performLog()
    {
        if (_logMessage == null)
        {
            throw new NullPointerException("LogMessage has not been set");
        }

        _actor.message(_logSubject, _logMessage);

        return _logger.getLogMessages();
    }

    /**
     * Validate that only a single log messasge occured and that the message
     * section starts with the specified tag
     *
     * @param logs     the logs generated during test run
     * @param tag      the tag to check for
     * @param expected
     *
     * @return the log message section for further testing
     */
    protected void validateLogMessage(List<Object> logs, String tag, String[] expected)
    {
        assertEquals("Log has incorrect message count", 1, logs.size());

        String log = String.valueOf(logs.get(0));

        int index = log.indexOf(_logSubject.toString());

        assertTrue("Unable to locate Subject:" + log, index != -1);

        String message = log.substring(index + _logSubject.toString().length());

        assertTrue("Message does not start with tag:" + tag + ":" + message,
                   message.startsWith(tag));

        // Test that the expected items occur in order.
        index = 0;
        for (String text : expected)
        {
            index = message.indexOf(text, index);
            assertTrue("Message does not contain expected (" + text + ") text :" + message, index != -1);
        }
    }

}
