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
import org.apache.qpid.AMQException;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.RootMessageLoggerImpl;
import org.apache.qpid.server.logging.rawloggers.UnitTestMessageLogger;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.List;

/**
 * Test : AMQPConnectionActorTest
 * Validate the AMQPConnectionActor class.
 *
 * The test creates a new AMQPActor and then logs a message using it.
 *
 * The test then verifies that the logged message was the only one created and
 * that the message contains the required message.
 */
public class AMQPConnectionActorTest extends TestCase
{

    LogActor _amqpActor;
    UnitTestMessageLogger _rawLogger;

    public void setUp() throws Exception, AMQException
    {
        super.setUp();
        //Highlight that this test will cause a new AR to be created
        ApplicationRegistry.getInstance();

        Configuration config = new PropertiesConfiguration();
        ServerConfiguration serverConfig = new ServerConfiguration(config);

        serverConfig.getConfig().setProperty(ServerConfiguration.STATUS_UPDATES, "on");

        setUpWithConfig(serverConfig);
    }

    public void tearDown() throws Exception
    {
        _rawLogger.clearLogMessages();

        // Correctly Close the AR we created
        ApplicationRegistry.remove();

        super.tearDown();        
    }

    private void setUpWithConfig(ServerConfiguration serverConfig) throws AMQException
    {
        _rawLogger = new UnitTestMessageLogger();
        RootMessageLogger rootLogger =
                new RootMessageLoggerImpl(serverConfig, _rawLogger);

        VirtualHost virtualHost = ApplicationRegistry.getInstance().
                getVirtualHostRegistry().getVirtualHosts().iterator().next();

        // Create a single session for this test.
        AMQProtocolSession session = new InternalTestProtocolSession(virtualHost);

        _amqpActor = new AMQPConnectionActor(session, rootLogger);
    }

    /**
     * Test the AMQPActor logging as a Connection level.
     *
     * The test sends a message then verifies that it entered the logs.
     *
     * The log message should be fully repalaced (no '{n}' values) and should
     * not contain any channel identification.
     */
    public void testConnection()
    {
        final String message = sendLogMessage();

        List<Object> logs = _rawLogger.getLogMessages();

        assertEquals("Message log size not as expected.", 1, logs.size());

        // Verify that the logged message is present in the output
        assertTrue("Message was not found in log message",
                   logs.get(0).toString().contains(message));

        // Verify that the message has the correct type
        assertTrue("Message contains the [con: prefix",
                   logs.get(0).toString().contains("[con:"));

        // Verify that all the values were presented to the MessageFormatter
        // so we will not end up with '{n}' entries in the log.
        assertFalse("Verify that the string does not contain any '{'.",
                    logs.get(0).toString().contains("{"));

        // Verify that the logged message does not contains the 'ch:' marker
        assertFalse("Message was logged with a channel identifier." + logs.get(0),
                    logs.get(0).toString().contains("/ch:"));
    }

    public void testConnectionLoggingOff() throws ConfigurationException, AMQException
    {
        Configuration config = new PropertiesConfiguration();
        config.addProperty("status-updates", "OFF");

        ServerConfiguration serverConfig = new ServerConfiguration(config);

        setUpWithConfig(serverConfig);

        sendLogMessage();

        List<Object> logs = _rawLogger.getLogMessages();

        assertEquals("Message log size not as expected.", 0, logs.size());

    }

    private String sendLogMessage()
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
        return message;
    }

}
