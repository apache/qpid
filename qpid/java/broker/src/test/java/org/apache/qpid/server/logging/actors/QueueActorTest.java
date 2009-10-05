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
package org.apache.qpid.server.logging.actors;

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
import org.apache.qpid.server.logging.rawloggers.UnitTestMessageLogger;
import org.apache.qpid.server.queue.MockAMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;

import java.util.List;

public class QueueActorTest extends TestCase
{
    LogActor _amqpActor;
    UnitTestMessageLogger _rawLogger;

    public void setUp() throws ConfigurationException
    {
        Configuration config = new PropertiesConfiguration();
        ServerConfiguration serverConfig = new ServerConfiguration(config);

        _rawLogger = new UnitTestMessageLogger();
        RootMessageLogger rootLogger =
                new RootMessageLoggerImpl(serverConfig, _rawLogger);

        MockAMQQueue queue = new MockAMQQueue(getName());

        queue.setVirtualHost(ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHosts().iterator().next());

        _amqpActor = new QueueActor(queue, rootLogger);
    }

    public void tearDown()
    {
        _rawLogger.clearLogMessages();
        ApplicationRegistry.remove();
    }

    /**
     * Test the QueueActor as a logger.
     *
     * The test logs a message then verifies that it entered the logs correctly
     *
     * The log message should be fully repalaced (no '{n}' values) and should
     * contain the correct queue identification.
     */
    public void testQueueActor()
    {
        final String message = "test logging";

        _amqpActor.message(new LogSubject()
        {
            public String toString()
            {
                return "[AMQPActorTest]";
            }

        }, new LogMessage()
        {
            public String toString()
            {
                return message;
            }
        });

        List<Object> logs = _rawLogger.getLogMessages();

        assertEquals("Message log size not as expected.", 1, logs.size());

        String log = logs.get(0).toString();

        // Verify that the logged message is present in the output
        assertTrue("Message was not found in log message",
                   log.contains(message));

        // Verify that all the values were presented to the MessageFormatter
        // so we will not end up with '{n}' entries in the log.
        assertFalse("Verify that the string does not contain any '{':" + log,
                    log.contains("{"));

        // Verify that the message has the correct type
        assertTrue("Message contains the [vh: prefix:" + log,
                   log.contains("[vh("));

        // Verify that the logged message contains the 'qu(' marker
        String expected = "qu(" + getName() + ")";
        assertTrue("Message was not logged with a queue identifer '"+expected+"' actual:" + log,
                    log.contains(expected));
    }

}

