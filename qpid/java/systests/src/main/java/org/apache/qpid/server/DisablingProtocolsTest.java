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
package org.apache.qpid.server;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * Tests to validate it is possible to disable support for particular protocol
 * versions entirely, rather than selectively excluding them on particular ports.
 */
public class DisablingProtocolsTest extends QpidBrokerTestCase
{
    public void setUp() throws Exception
    {
        // No-op, we call super.setUp() from test methods after appropriate config overrides
    }

    /**
     * Test that 0-10, 0-9-1, 0-9, and 0-8 support is present when no
     * attempt has yet been made to disable them, and forcing the client
     * to negotiate from a particular protocol version returns a connection
     * using the expected protocol version.
     */
    public void testDefaultProtocolSupport() throws Exception
    {
        //Start the broker without modifying its supported protocols
        super.setUp();

        //Verify requesting a 0-10 connection works
        setTestClientSystemProperty(ClientProperties.AMQP_VERSION, "0-10");
        AMQConnection connection = (AMQConnection) getConnection();
        assertEquals("Unexpected protocol version in use", ProtocolVersion.v0_10, connection.getProtocolVersion());
        connection.close();

        //Verify requesting a 0-91 connection works
        setTestClientSystemProperty(ClientProperties.AMQP_VERSION, "0-9-1");
        connection = (AMQConnection) getConnection();
        assertEquals("Unexpected protocol version in use", ProtocolVersion.v0_91, connection.getProtocolVersion());
        connection.close();

        //Verify requesting a 0-9 connection works
        setTestClientSystemProperty(ClientProperties.AMQP_VERSION, "0-9");
        connection = (AMQConnection) getConnection();
        assertEquals("Unexpected protocol version in use", ProtocolVersion.v0_9, connection.getProtocolVersion());
        connection.close();

        //Verify requesting a 0-8 connection works
        setTestClientSystemProperty(ClientProperties.AMQP_VERSION, "0-8");
        connection = (AMQConnection) getConnection();
        assertEquals("Unexpected protocol version in use", ProtocolVersion.v8_0, connection.getProtocolVersion());
        connection.close();
    }

    public void testDisabling010() throws Exception
    {
        //disable 0-10 support
        setConfigurationProperty("connector.amqp010enabled", "false");

        super.setUp();

        //Verify initially requesting a 0-10 connection now negotiates a 0-91
        //connection as the broker should reply with its highest supported protocol
        setTestClientSystemProperty(ClientProperties.AMQP_VERSION, "0-10");
        AMQConnection connection = (AMQConnection) getConnection();
        assertEquals("Unexpected protocol version in use", ProtocolVersion.v0_91, connection.getProtocolVersion());
        connection.close();
    }

    public void testDisabling091and010() throws Exception
    {
        //disable 0-91 and 0-10 support
        setConfigurationProperty("connector.amqp010enabled", "false");
        setConfigurationProperty("connector.amqp091enabled", "false");

        super.setUp();

        //Verify initially requesting a 0-10 connection now negotiates a 0-9
        //connection as the broker should reply with its highest supported protocol
        setTestClientSystemProperty(ClientProperties.AMQP_VERSION, "0-10");
        AMQConnection connection = (AMQConnection) getConnection();
        assertEquals("Unexpected protocol version in use", ProtocolVersion.v0_9, connection.getProtocolVersion());
        connection.close();
    }

    public void testDisabling09and091and010() throws Exception
    {
        //disable 0-9, 0-91 and 0-10 support
        setConfigurationProperty("connector.amqp09enabled", "false");
        setConfigurationProperty("connector.amqp091enabled", "false");
        setConfigurationProperty("connector.amqp010enabled", "false");

        super.setUp();

        //Verify initially requesting a 0-10 connection now negotiates a 0-8
        //connection as the broker should reply with its highest supported protocol
        setTestClientSystemProperty(ClientProperties.AMQP_VERSION, "0-10");
        AMQConnection connection = (AMQConnection) getConnection();
        assertEquals("Unexpected protocol version in use", ProtocolVersion.v8_0, connection.getProtocolVersion());
        connection.close();
    }
}